package inceris.custommusic.battle

import com.cobblemon.mod.common.client.sound.BattleMusicController
import inceris.custommusic.audio.CobblemonMusicAdapter
import inceris.custommusic.audio.MusicProgram
import inceris.custommusic.audio.MusicStage
import inceris.custommusic.rules.MusicRule
import inceris.custommusic.rules.RuleLoader
import inceris.custommusic.rules.RuleMatcher
import inceris.custommusic.rules.RuleSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger

/** Client-thread state. Trainer identity persists when its entity unloads; teams never drive trainer themes by default. */
class BattleLifecycleHandler(private val loader: RuleLoader, private val logger: Logger) {
    private val resolver = BattleContextResolver()
    private val matcher = RuleMatcher()
    private val program = MusicProgram()
    private var context: BattleContext? = null
    private var defaultSound: ResourceLocation? = null
    private var suspended = false
    private var lastSnapshot: RuleSnapshot? = null
    private var lastContext: BattleContext? = null
    private var selectedSound: ResourceLocation? = null
    private var matchedRule: MusicRule? = null
    private var lastLog: Pair<String?, ResourceLocation?>? = null
    private val warnings = mutableSetOf<String>()

    fun resolveMusic(original: ResourceLocation): ResourceLocation {
        val client = Minecraft.getInstance()
        refreshContext(client)
        defaultSound = original
        suspended = false
        // Sound resources may have changed, so validate again on every server music request.
        lastSnapshot = null
        return choose(client) ?: original
    }

    fun onMusicHandled() {
        if (BattleMusicController.music.isFading()) suspended = true
        observeIntro(Minecraft.getInstance())
    }

    fun isIntro(sound: ResourceLocation): Boolean =
        !suspended && program.desired?.let { it.stage == MusicStage.INTRO && it.sound == sound.toString() } == true

    private fun observeIntro(client: Minecraft) {
        val current = BattleMusicController.music
        if (isIntro(current.location)) program.observeIntro(client.soundManager.isActive(current))
    }

    fun tick(client: Minecraft) {
        refreshContext(client)
        if (context == null || suspended || defaultSound == null) return
        observeIntro(client)
        val sound = choose(client) ?: defaultSound ?: return
        CobblemonMusicAdapter.switchTo(sound, client, looping = !isIntro(sound))
    }

    private fun refreshContext(client: Minecraft) {
        val resolved = resolver.resolve(client)
        if (resolved == null) {
            if (context != null) {
                CobblemonMusicAdapter.end()
                clear()
            }
            return
        }
        val previous = context
        if (previous?.battleId != resolved.battleId) {
            clear()
            context = resolved
            logger.debug("Battle context: {}", resolved)
        } else {
            // Late synchronized entities can enrich identity. Retain it if the entity leaves tracking range.
            context = resolved.copy(trainers = resolved.trainers.mapIndexed { index, trainer ->
                if (trainer.id != null) trainer else previous.trainers.getOrNull(index) ?: trainer
            }, players = resolved.players.map { player ->
                if (player.username != null) player else previous.players.firstOrNull { it.uuid == player.uuid } ?: player
            })
        }
    }

    private fun choose(client: Minecraft): ResourceLocation? {
        val battle = context ?: return null
        val snapshot = loader.snapshot
        if (lastSnapshot !== snapshot || lastContext != battle) {
            lastSnapshot = snapshot
            lastContext = battle
            val selection = matcher.select(battle, snapshot)
            if (selection.tied.size > 1) warnOnce("tie:${selection.tied.map { it.rule.key }}",
                "Equal-priority battle music tie; using ${selection.winner?.rule?.key}. Candidates: ${selection.tied.map { it.rule.key }}")
            matchedRule = selection.winner?.rule
        }
        val track = program.select(matchedRule, battle.lowAllyHealth, battle.lowEnemyHealth) { name ->
            val sound = ResourceLocation.tryParse(name)
            val available = sound != null && CobblemonMusicAdapter.isAvailable(sound, client)
            if (!available) warnOnce("missing:$name", "Missing or empty sound $name (${matchedRule?.key}); skipping this playback stage")
            available
        }
        selectedSound = track?.sound?.let(ResourceLocation::tryParse)
        val log = matchedRule?.key to selectedSound
        if (lastLog != log) logger.debug("Battle {} theme: {} (rule {}, stage {})", battle.battleId,
            selectedSound ?: defaultSound, matchedRule?.key ?: "default", track?.stage ?: "default")
        lastLog = log
        return selectedSound
    }

    private fun warnOnce(key: String, message: String) {
        if (warnings.add(key)) logger.warn(message)
    }

    fun clear() {
        program.clear()
        context = null
        defaultSound = null
        selectedSound = null
        matchedRule = null
        lastLog = null
        lastSnapshot = null
        lastContext = null
        suspended = false
        warnings.clear()
    }

    fun disconnect() {
        if (context != null) CobblemonMusicAdapter.end()
        clear()
    }
}
