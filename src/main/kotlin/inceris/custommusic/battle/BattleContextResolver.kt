package inceris.custommusic.battle

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import inceris.custommusic.rules.SpeciesCategories
import inceris.custommusic.rules.normalize
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

class BattleContextResolver(private val identities: TrainerIdentityResolver = TrainerIdentityResolver()) {
    fun resolve(client: Minecraft): BattleContext? {
        val battle = CobblemonClient.battle ?: return null
        if (battle.spectating || client.player == null) return null
        // ClientBattle.side1 is the participating player's side; side2 contains actual opponents.
        val actors = battle.side2.actors
        val kind = when {
            actors.any { it.type == ActorType.NPC } -> BattleKind.TRAINER
            actors.isNotEmpty() && actors.all { it.type == ActorType.WILD } -> BattleKind.WILD
            else -> BattleKind.PLAYER
        }
        val trainers = actors.filter { it.type == ActorType.NPC }.map { identities.resolve(it, client) }
        val opponents = actors.flatMap { it.activePokemon }.mapNotNull { it.battlePokemon }.mapNotNull { pokemon ->
            val raw = pokemon.properties.species ?: return@mapNotNull null
            val speciesId = ResourceLocation.tryParse(if (':' in raw) normalize(raw) else "cobblemon:${normalize(raw)}")
                ?: return@mapNotNull null
            val species = PokemonSpecies.getByIdentifier(speciesId)
            val form = pokemon.properties.form?.let(::normalize)
                ?: species?.getForm(pokemon.state.currentAspects)?.formOnlyShowdownId()?.let(::normalize)
                ?: "normal"
            val nativeLabels = species?.getForm(pokemon.state.currentAspects)?.labels.orEmpty()
            OpponentPokemon(speciesId.toString(), form, PokemonTraits.categories(
                nativeLabels + SpeciesCategories.labels(speciesId.toString(), form),
                pokemon.properties.shiny == true || "shiny" in pokemon.state.currentAspects
            ))
        }
        val players = actors.filter { it.type == ActorType.PLAYER }.map {
            PlayerIdentity(it.uuid, client.connection?.getPlayerInfo(it.uuid)?.profile?.name)
        }
        // Count all living active Pokemon on each side, including teammates in multi battles.
        val lowAllyHealth = battle.side1.actors.flatMap { it.activePokemon }
            .mapNotNull { it.battlePokemon }.any { PokemonTraits.isLowHealth(it.hpValue, it.maxHp, it.isHpFlat) }
        val lowEnemyHealth = actors.flatMap { it.activePokemon }
            .mapNotNull { it.battlePokemon }.any { PokemonTraits.isLowHealth(it.hpValue, it.maxHp, it.isHpFlat) }
        return BattleContext(battle.battleId, kind, trainers, opponents, players = players,
            lowAllyHealth = lowAllyHealth, lowEnemyHealth = lowEnemyHealth)
    }
}
