package inceris.custommusic

import inceris.custommusic.battle.*
import inceris.custommusic.rules.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class ExtendedRulesTest {
    private val parser = RuleParser()
    private val matcher = RuleMatcher()
    private val id = UUID(0, 1)
    private fun document(vararg rules: String) = parser.parse("""{"id":"test","rules":[${rules.joinToString(",")}]}""".reader(), "test")
    private fun snapshot(vararg rules: String): RuleSnapshot {
        val parsed = document(*rules)
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.toString())
        return RuleSnapshot(parsed.rules)
    }

    @Test fun `wild player and all defaults select by battle kind`() {
        val rules = snapshot("""{"type":"all","sound":["test:all"]}""", """{"type":"wild","sound":["test:wild"]}""",
            """{"type":"player","sound":["test:player"]}""")
        assertEquals("test:wild", matcher.select(BattleContext(id, BattleKind.WILD), rules).winner!!.rule.sound.single())
        assertEquals("test:player", matcher.select(BattleContext(id, BattleKind.PLAYER), rules).winner!!.rule.sound.single())
        assertEquals("test:all", matcher.select(BattleContext(id, BattleKind.TRAINER), rules).winner!!.rule.sound.single())
        assertNull(matcher.select(BattleContext(id, BattleKind.PLAYER, spectating = true), rules).winner)
    }

    @Test fun `UUID targeting outranks username then player default and tolerates missing profiles`() {
        val rules = snapshot("""{"type":"player","sound":["test:default"]}""",
            """{"type":"player","match":"ASH","sound":["test:name"]}""",
            """{"type":"player","match":"$id","sound":["test:uuid"]}""")
        val context = BattleContext(UUID(0, 2), BattleKind.PLAYER, players = listOf(PlayerIdentity(id, "Ash")))
        assertEquals("test:uuid", matcher.select(context, rules).winner!!.rule.sound.single())
        assertEquals("test:uuid", matcher.select(context.copy(players = listOf(PlayerIdentity(id))), rules).winner!!.rule.sound.single())
        assertEquals("test:name", matcher.select(context, rules.copy(rules = rules.rules.take(2))).winner!!.rule.sound.single())
        assertEquals("test:default", matcher.select(context.copy(players = listOf(PlayerIdentity(UUID(0, 3), "Ash Jr"))), rules).winner!!.rule.sound.single())
    }

    @Test fun `specific species beats wild default and priority overrides specificity`() {
        val rules = snapshot("""{"type":"wild","sound":["test:wild"]}""",
            """{"type":"species","match":"cobblemon:eevee","sound":["test:eevee"]}""")
        val context = BattleContext(id, BattleKind.WILD, opponents = listOf(OpponentPokemon("cobblemon:eevee")))
        assertEquals(RuleType.SPECIES, matcher.select(context, rules).winner!!.rule.type)
        assertEquals(RuleType.WILD, matcher.select(context, rules.copy(rules = rules.rules.map { if (it.type == RuleType.WILD) it.copy(priority = 1) else it })).winner!!.rule.type)
    }

    @Test fun `player scope isolates species rules from wild and NPC battles`() {
        val rules = snapshot("""{"type":"species","match":"cobblemon:eevee","scope":"player","sound":["test:eevee"]}""")
        val context = BattleContext(id, BattleKind.PLAYER, opponents = listOf(OpponentPokemon("cobblemon:eevee")))
        assertNotNull(matcher.select(context, rules).winner)
        assertNull(matcher.select(context.copy(kind = BattleKind.WILD), rules).winner)
        assertNull(matcher.select(context.copy(kind = BattleKind.TRAINER), rules).winner)
    }

    @Test fun `spawn rules cannot change battle music and use their own ranking`() {
        val rules = snapshot("""{"type":"all","sound":["test:battle"]}""",
            """{"type":"wild","event":"spawn","sound":["test:appearance"]}""",
            """{"type":"category","match":"shiny","event":"spawn","sound":["test:shiny"]}""")
        val context = BattleContext(id, BattleKind.WILD, opponents = listOf(OpponentPokemon("cobblemon:eevee", categories = setOf("shiny"))))
        assertEquals("test:battle", matcher.select(context, rules).winner!!.rule.sound.single())
        assertEquals("test:shiny", matcher.select(context, rules, RuleEvent.SPAWN).winner!!.rule.sound.single())
        assertEquals(RuleScope.WILD, rules.rules.last().scope)
    }

    @Test fun `new rule shapes and optional stages are strictly validated`() {
        val valid = document("""{"type":"all","sound":["test:main"],"intro":["test:intro"],"low_ally_hp":["test:low"],"low_enemy_hp":["test:enemy"]}""")
        assertTrue(valid.diagnostics.isEmpty())
        assertEquals(listOf("test:intro"), valid.rules.single().intro)
        assertEquals(listOf("test:low"), valid.rules.single().lowAllyHp)
        assertEquals(listOf("test:enemy"), valid.rules.single().lowEnemyHp)
        for (rule in listOf(
            """{"type":"wild","match":"anything","sound":["test:x"]}""",
            """{"type":"all","match":"anything","sound":["test:x"]}""",
            """{"type":"player","match":"not-a-uuid-or-name","sound":["test:x"]}""",
            """{"type":"player","scope":"any","sound":["test:x"]}""",
            """{"type":"wild","scope":"player","sound":["test:x"]}""",
            """{"type":"all","intro":[],"sound":["test:x"]}""",
            """{"type":"all","low_hp":["test:legacy"],"sound":["test:x"]}""",
            """{"type":"all","low_ally_hp":"test:scalar","sound":["test:x"]}""",
            """{"type":"all","low_enemy_hp":"test:scalar","sound":["test:x"]}""",
            """{"type":"all","low_ally_hp":[],"sound":["test:x"]}""",
            """{"type":"all","low_enemy_hp":[],"sound":["test:x"]}""",
            """{"type":"all","low_ally_hp":["Bad:ID"],"sound":["test:x"]}""",
            """{"type":"all","low_enemy_hp":["Bad:ID"],"sound":["test:x"]}""",
            """{"type":"all","intro":["Bad:ID"],"sound":["test:x"]}""",
            """{"type":"player","event":"spawn","sound":["test:x"]}""",
            """{"type":"all","event":"spawn","intro":["test:intro"],"sound":["test:x"]}""",
            """{"type":"all","event":"spawn","low_ally_hp":["test:low"],"sound":["test:x"]}""",
            """{"type":"all","event":"spawn","low_enemy_hp":["test:low"],"sound":["test:x"]}""",
            """{"type":"all","event":"spawn","scope":"any","sound":["test:x"]}"""
        )) {
            val parsed = document(rule)
            assertTrue(parsed.rules.isEmpty(), rule)
            assertEquals(1, parsed.diagnostics.size, rule)
        }
    }

    @Test fun `low HP tracks are independently optional`() {
        val ally = document("""{"type":"wild","sound":["test:main"],"low_ally_hp":["test:ally"]}""")
        val enemy = document("""{"type":"wild","sound":["test:main"],"low_enemy_hp":["test:enemy"]}""")
        val neither = document("""{"type":"wild","sound":["test:main"]}""")
        assertTrue((ally.diagnostics + enemy.diagnostics + neither.diagnostics).isEmpty())
        assertEquals(listOf("test:ally"), ally.rules.single().lowAllyHp)
        assertTrue(ally.rules.single().lowEnemyHp.isEmpty())
        assertEquals(listOf("test:enemy"), enemy.rules.single().lowEnemyHp)
        assertTrue(enemy.rules.single().lowAllyHp.isEmpty())
        assertTrue(neither.rules.single().lowAllyHp.isEmpty())
        assertTrue(neither.rules.single().lowEnemyHp.isEmpty())
    }

    @Test fun `shiny membership follows individuals and cannot be overridden by a static category`() {
        assertEquals(setOf("legendary", "shiny"), PokemonTraits.categories(setOf("legendary"), true))
        assertEquals(setOf("legendary"), PokemonTraits.categories(setOf("legendary", "shiny"), false))
        val parsed = parser.parse("""{"id":"test","rules":[],"categories":{"SHINY":["cobblemon:eevee"]}}""".reader(), "test")
        assertTrue(parsed.categories.isEmpty())
        assertEquals(1, parsed.diagnostics.size)
    }

    @Test fun `red HP boundary excludes fainted Pokemon and handles fractional health`() {
        assertTrue(PokemonTraits.isLowHealth(20f, 100f, true))
        assertFalse(PokemonTraits.isLowHealth(20.01f, 100f, true))
        assertTrue(PokemonTraits.isLowHealth(0.2f, 0f, false))
        assertFalse(PokemonTraits.isLowHealth(0.201f, 0f, false))
        assertFalse(PokemonTraits.isLowHealth(0f, 100f, true))
        assertFalse(PokemonTraits.isLowHealth(1f, 0f, true))
        assertFalse(PokemonTraits.isLowHealth(Float.NaN, 100f, true))
    }
}
