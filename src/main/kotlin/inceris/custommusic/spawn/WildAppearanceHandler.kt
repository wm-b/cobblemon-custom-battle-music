package inceris.custommusic.spawn

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket
import inceris.custommusic.audio.CobblemonMusicAdapter
import inceris.custommusic.battle.BattleContext
import inceris.custommusic.battle.BattleKind
import inceris.custommusic.battle.OpponentPokemon
import inceris.custommusic.battle.PokemonTraits
import inceris.custommusic.rules.*
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.Logger
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** Client tracking notifications, verified from fully decoded Cobblemon ownership metadata. */
class WildAppearanceHandler(private val loader: RuleLoader, historyDirectory: Path, private val logger: Logger,
                            legacyHistoryDirectory: Path? = null) {
    private data class Candidate(val pokemon: UUID, val entityId: Int, val level: ClientLevel)
    private val pending = linkedMapOf<UUID, Candidate>()
    private val matcher = RuleMatcher()
    private val history = PersistentSpawnHistory(historyDirectory, legacyHistoryDirectory)
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "custommusic-appearance-history").apply { isDaemon = true } }
    private var generation = 0L
    private val warnings = mutableSetOf<String>()

    fun onSpawn(packet: SpawnPokemonPacket) {
        if (!isWild(packet.ownerId, packet.storageUUID)) return
        val level = Minecraft.getInstance().level ?: return
        pending[packet.pokemonUUID] = Candidate(packet.pokemonUUID, packet.vanillaSpawnPacket.id, level)
    }

    fun tick(client: Minecraft) {
        if (pending.isEmpty() || client.player == null) return
        val identity = scope(client) ?: return
        val batch = pending.values.toList()
        pending.clear()
        batch.forEach { candidate ->
            if (candidate.level !== client.level) return@forEach
            val entity = candidate.level.getEntity(candidate.entityId) as? PokemonEntity ?: return@forEach
            if (entity.pokemon.uuid != candidate.pokemon || entity.ownerUUID != null) return@forEach
            val pokemon = entity.pokemon
            val species = pokemon.species.resourceIdentifier.toString()
            val form = pokemon.form.formOnlyShowdownId()
            val categories = PokemonTraits.categories(pokemon.form.labels + SpeciesCategories.labels(species, form), pokemon.shiny)
            val context = BattleContext(candidate.pokemon, BattleKind.WILD, opponents = listOf(OpponentPokemon(species, form, categories)))
            val rule = matcher.select(context, loader.snapshot, RuleEvent.SPAWN).winner?.rule ?: return@forEach
            val sound = ResourceLocation.tryParse(rule.sound.random()) ?: return@forEach
            if (!CobblemonMusicAdapter.isAvailable(sound, client)) {
                if (warnings.add("missing:$sound")) logger.warn("Missing appearance sound {} ({}); skipping", sound, rule.key)
                return@forEach
            }
            val token = generation
            CompletableFuture.supplyAsync({ history.claim(identity, candidate.pokemon) }, writer)
                .whenComplete { claimed, error ->
                    if (error != null) logger.warn("Unable to record appearance sound: {}", error.cause?.message ?: error.message)
                    else if (claimed == true) client.execute {
                        if (token != generation || client.level !== candidate.level || entity.isRemoved || entity.ownerUUID != null) return@execute
                        // NEUTRAL remains audible during battle music; this never enters the battle controller.
                        client.soundManager.play(SimpleSoundInstance(SoundEvent.createVariableRangeEvent(sound), SoundSource.NEUTRAL,
                            1f, 1f, RandomSource.create(), entity.x, entity.y, entity.z))
                    }
                }
        }
    }

    fun disconnect() {
        generation++
        pending.clear()
        warnings.clear()
        writer.execute { runCatching { history.close() }.onFailure { logger.warn("Unable to close appearance history: {}", it.message) } }
    }

    private fun scope(client: Minecraft): String? {
        val world = client.singleplayerServer?.getWorldPath(LevelResource.ROOT)?.toAbsolutePath()?.normalize()
        val server = client.currentServer?.ip?.let(::normalize)
        val location = world?.let { "world:$it" } ?: server?.let { "server:$it" } ?: return null
        return "$location|listener:${client.user.profileId}"
    }

    companion object {
        fun isWild(owner: UUID?, storage: UUID?): Boolean = owner == null && storage == null
    }
}
