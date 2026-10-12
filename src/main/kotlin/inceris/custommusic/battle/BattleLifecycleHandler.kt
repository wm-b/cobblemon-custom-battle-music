package inceris.custommusic.battle

import com.cobblemon.mod.common.client.sound.BattleMusicController
import inceris.custommusic.audio.CobblemonMusicAdapter
import inceris.custommusic.audio.RandomTrackSelector
import inceris.custommusic.rules.RuleLoader
import inceris.custommusic.rules.RuleMatcher
import inceris.custommusic.rules.RuleSnapshot
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger

/** Client-thread state. Trainer identity persists when its entity unloads; teams never drive trainer themes by default. */
class BattleLifecycleHandler(private val loader: RuleLoader, private val logger: Logger, private val debug: Boolean) {
    private val resolver = BattleContextResolver()
    private val matcher = RuleMatcher()
    private val tracks = RandomTrackSelector()
    private var context: BattleContext? = null
    private var defaultSound: ResourceLocation? = null
    private var suspended = false
    private var lastSnapshot: RuleSnapshot? = null
    private var lastContext: BattleContext? = null
    private var selectedSound: ResourceLocation? = null
    private var matchedSound: ResourceLocation? = null
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
    }

    fun tick(client: Minecraft) {
        refreshContext(client)
        if (context == null || suspended || defaultSound == null) return
        val sound = choose(client) ?: defaultSound ?: return
        CobblemonMusicAdapter.switchTo(sound, client)
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
            if (debug) logger.info("Battle context: {}", resolved)
        } else {
            // Late synchronized entities can enrich identity. Retain it if the entity leaves tracking range.
            context = previous.copy(trainers = resolved.trainers.mapIndexed { index, trainer ->
                if (trainer.id != null) trainer else previous.trainers.getOrNull(index) ?: trainer
            }, opponents = resolved.opponents)
        }
    }

    private fun choose(client: Minecraft): ResourceLocation? {
        val battle = context ?: return null
        val snapshot = loader.snapshot
        if (lastSnapshot === snapshot && lastContext == battle) {
            // Sound-manager reload and rule reload can complete at different times.
            selectedSound = matchedSound?.takeIf { CobblemonMusicAdapter.isAvailable(it, client) }
            return selectedSound
        }
        lastSnapshot = snapshot
        lastContext = battle
        val selection = matcher.select(battle, snapshot)
        if (selection.tied.size > 1) warnOnce("tie:${selection.tied.map { it.rule.key }}",
            "Equal-priority battle music tie; using ${selection.winner?.rule?.key}. Candidates: ${selection.tied.map { it.rule.key }}")
        val rule = selection.winner?.rule
        val sound = tracks.select(rule)?.let(ResourceLocation::tryParse)
        matchedSound = sound
        selectedSound = if (sound != null && CobblemonMusicAdapter.isAvailable(sound, client)) sound else null
        if (sound != null && selectedSound == null) warnOnce("missing:$sound", "Missing or empty sound $sound (${rule?.key}); using Cobblemon's theme")
        if (debug) logger.info("Battle {} theme: {} (rule {})", battle.battleId, selectedSound ?: defaultSound, rule?.key ?: "default")
        return selectedSound
    }

    private fun warnOnce(key: String, message: String) {
        if (warnings.add(key)) logger.warn(message)
    }

    fun clear() {
        tracks.clear()
        context = null
        defaultSound = null
        selectedSound = null
        matchedSound = null
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
