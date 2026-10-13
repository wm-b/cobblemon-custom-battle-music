package inceris.custommusic

import com.google.gson.JsonParser
import inceris.custommusic.battle.*
import inceris.custommusic.rules.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

/** Test the shipped command menu's expectations using the actual parser and matcher. */
class PrismaExamplesTest {
    private val root = Path.of("examples/prisma-tests")
    private fun parse(path: Path) = Files.newBufferedReader(path).use { RuleParser().parse(it, path.toString()) }.also {
        assertTrue(it.diagnostics.isEmpty(), "${path}: ${it.diagnostics}")
    }

    @Test fun `complete menu scenarios select their documented battle and appearance sounds`() {
        val parsed = parse(root.resolve("resourcepack/assets/prisma_test/battle_music/music.json"))
        val snapshot = RuleSnapshot(parsed.rules, parsed.categories)
        val scenarios = Files.newBufferedReader(root.resolve("scenarios.json")).use { JsonParser.parseReader(it).asJsonArray }
        val matcher = RuleMatcher()
        for (scenario in scenarios) {
            val row = scenario.asJsonObject
            val expected = row.getAsJsonObject("expect") ?: continue
            fun text(name: String, fallback: String) = expected[name]?.asString ?: fallback
            val species = text("species", "cobblemon:rattata")
            val form = text("form", "normal")
            val pokemon = OpponentPokemon(species, form, PokemonTraits.categories(
                SpeciesCategories.labels(species, form), expected["shiny"]?.asBoolean == true))
            val kind = BattleKind.valueOf(text("kind", "wild").uppercase())
            val trainers = if (kind == BattleKind.TRAINER) listOf(TrainerIdentity(
                text("trainerId", "prisma_test:default"), text("trainerName", "Prisma Default Trainer"))) else emptyList()
            val players = if (kind == BattleKind.PLAYER) listOf(PlayerIdentity(
                UUID.fromString(text("playerUuid", "00000000-0000-0000-0000-000000000042")), expected["playerName"]?.asString)) else emptyList()
            val context = BattleContext(UUID(0, 1), kind, trainers, listOf(pokemon), players = players)
            val event = RuleEvent.valueOf(text("event", "battle").uppercase())
            val winner = matcher.select(context, snapshot, event).winner
            assertEquals(expected["sounds"].asJsonArray.map { it.asString }, winner?.rule?.sound, row["id"].asString)
            assertNull(matcher.select(context.copy(spectating = true), snapshot, event).winner)
        }
    }

    @Test fun `isolated variants parse and expose their intended selectors`() {
        val noRules = parse(root.resolve("variants/no-rules.json"))
        assertTrue(noRules.rules.isEmpty())
        for ((name, type) in listOf("spawn-all" to RuleType.ALL, "spawn-wild" to RuleType.WILD)) {
            val rule = parse(root.resolve("variants/$name.json")).rules.single()
            assertEquals(type, rule.type)
            assertEquals(RuleEvent.SPAWN, rule.event)
            assertEquals(RuleScope.WILD, rule.scope)
        }
        val named = parse(root.resolve("variants/player-name.json"))
        assertEquals(2, named.rules.size)
        assertTrue(named.rules.all { it.type == RuleType.PLAYER })
    }
}
