package inceris.custommusic

import inceris.custommusic.battle.*
import inceris.custommusic.rules.*
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID
import kotlin.test.*

class RuleMatcherTest {
    private val matcher = RuleMatcher()
    private val id = UUID(0, 1)
    private fun trainer(identity: String? = "gym:kanto/brock", name: String = "Brock") =
        BattleContext(id, BattleKind.TRAINER, listOf(TrainerIdentity(identity, name)), listOf(OpponentPokemon("cobblemon:mewtwo", "normal", setOf("legendary"))))
    private fun wild(vararg pokemon: OpponentPokemon) = BattleContext(id, BattleKind.WILD, opponents = pokemon.toList())
    private fun rule(type: RuleType, match: String, priority: Int = 0, form: String? = null, source: String = match,
                     scope: RuleScope = if (type in setOf(RuleType.TRAINER, RuleType.TRAINER_NAME)) RuleScope.TRAINER else RuleScope.WILD) =
        MusicRule(source, "test", 0, type, match, listOf("test:theme"), priority, form, scope)
    private fun winner(context: BattleContext, vararg rules: MusicRule) = matcher.select(context, RuleSnapshot(rules.toList())).winner?.rule

    @Test fun `exact trainer beats longest prefix which beats broad prefix`() {
        val broad = rule(RuleType.TRAINER, "gym")
        val region = rule(RuleType.TRAINER, "gym:kanto")
        val exact = rule(RuleType.TRAINER, "gym:kanto/brock")
        assertEquals(exact, winner(trainer(), broad, region, exact))
        assertEquals(region, winner(trainer(), broad, region))
        assertEquals(broad, winner(trainer(), broad))
    }

    @Test fun `trainer prefixes respect colon and slash boundaries`() {
        assertFalse(RuleMatcher.matchesTrainerPrefix("gym", "gymnasium:kanto/brock"))
        assertFalse(RuleMatcher.matchesTrainerPrefix("gym:kanto", "gym:kantonia/brock"))
        assertFalse(RuleMatcher.matchesTrainerPrefix("gym:johto", "gym:kanto/brock"))
        assertFalse(RuleMatcher.matchesTrainerPrefix("", "gym:kanto/brock"))
        assertTrue(RuleMatcher.matchesTrainerPrefix(" GYM:KANTO ", "gym:kanto/brock"))
        assertTrue(RuleMatcher.matchesTrainerPrefix("gym:kanto/brock", "gym:kanto/brock"))
    }

    @Test fun `higher explicit priority overrides exact identity`() {
        val broad = rule(RuleType.TRAINER, "gym", priority = 101)
        assertEquals(broad, winner(trainer(), rule(RuleType.TRAINER, "gym:kanto/brock", priority = 100), broad))
    }

    @Test fun `normalization is independent of Turkish locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val rule = rule(RuleType.TRAINER, "GYM:KANTO")
            assertEquals(rule, winner(trainer("GYM:KANTO/BROCK"), rule))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun `display name is exact and available without native id`() {
        val name = rule(RuleType.TRAINER_NAME, "brock")
        assertEquals(name, winner(trainer(null, "BROCK"), name))
        assertNull(winner(trainer(null, "Brock Jr"), name))
    }

    @Test fun `exact name and exact id share a deterministic rank`() {
        val name = rule(RuleType.TRAINER_NAME, "brock", source = "b")
        val exact = rule(RuleType.TRAINER, "gym:kanto/brock", source = "a")
        val selection = matcher.select(trainer(), RuleSnapshot(listOf(name, exact)))
        assertEquals(exact, selection.winner?.rule)
        assertEquals(2, selection.tied.size)
    }

    @Test fun `form beats species which beats category`() {
        val battle = wild(OpponentPokemon("cobblemon:mewtwo", "megax", setOf("legendary")))
        val category = rule(RuleType.CATEGORY, "legendary")
        val species = rule(RuleType.SPECIES, "cobblemon:mewtwo")
        val form = rule(RuleType.SPECIES, "cobblemon:mewtwo", form = "MegaX", source = "form")
        assertEquals(form, winner(battle, category, species, form))
        assertEquals(species, winner(battle, category, species))
        assertEquals(category, winner(battle, category))
        assertNull(winner(wild(OpponentPokemon("cobblemon:mewtwo", "normal")), form))
    }

    @Test fun `higher category priority overrides species specificity`() {
        val category = rule(RuleType.CATEGORY, "legendary", priority = 1)
        assertEquals(category, winner(wild(OpponentPokemon("cobblemon:mewtwo", categories = setOf("legendary"))),
            rule(RuleType.SPECIES, "cobblemon:mewtwo"), category))
    }

    @Test fun `wild rules do not change trainer themes or player battles`() {
        val species = rule(RuleType.SPECIES, "cobblemon:mewtwo", priority = 10000)
        val category = rule(RuleType.CATEGORY, "legendary", priority = 10000)
        assertNull(winner(trainer(), species, category))
        assertNull(winner(trainer().copy(kind = BattleKind.PLAYER), species, category))
        val anyScope = species.copy(scope = RuleScope.ANY)
        assertEquals(anyScope, winner(trainer(), anyScope))
    }

    @Test fun `custom categories can target a species or form`() {
        val category = rule(RuleType.CATEGORY, "boss")
        val snapshot = RuleSnapshot(listOf(category), mapOf("boss" to setOf(SpeciesSelector("cobblemon:mewtwo", "megax"))))
        assertEquals(category, matcher.select(wild(OpponentPokemon("cobblemon:mewtwo", "megax")), snapshot).winner?.rule)
        assertNull(matcher.select(wild(OpponentPokemon("cobblemon:mewtwo", "normal")), snapshot).winner)
    }

    @Test fun `multi opponent result and ties are independent of input order`() {
        val a = rule(RuleType.SPECIES, "cobblemon:mewtwo", source = "a")
        val b = rule(RuleType.SPECIES, "cobblemon:mew", source = "b")
        val opponents = listOf(OpponentPokemon("cobblemon:mew"), OpponentPokemon("cobblemon:mewtwo"))
        val selection = matcher.select(wild(*opponents.toTypedArray()), RuleSnapshot(listOf(b, a)))
        assertEquals(a, selection.winner?.rule)
        assertEquals(selection, matcher.select(wild(*opponents.reversed().toTypedArray()), RuleSnapshot(listOf(a, b))))
        assertEquals(2, selection.tied.size)
    }

    @Test fun `no matches and spectators fall back`() {
        assertNull(winner(wild(OpponentPokemon("cobblemon:eevee")), rule(RuleType.CATEGORY, "legendary")))
        assertNull(winner(trainer().copy(spectating = true), rule(RuleType.TRAINER, "gym")))
        assertNull(winner(trainer()))
    }
}
