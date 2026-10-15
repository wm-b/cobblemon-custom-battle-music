package inceris.custommusic

import inceris.custommusic.rules.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RuleParserTest {
    private val parser = RuleParser()
    private fun parse(json: String) = parser.parse(json.reader(), "test.json")

    @Test fun `sound arrays preserve all tracks and their order`() {
        val result = parse("""{"id":"music","rules":[
            {"type":"trainer","match":"gym","sound":["test:first","test:second","test:third"]}
        ]}""")
        assertTrue(result.diagnostics.isEmpty())
        assertEquals(listOf("test:first", "test:second", "test:third"), result.rules.single().sound)
    }

    @Test fun `trainer wildcard rules parse and match an NPC class ID`() {
        for (pattern in listOf("*leader*", " COBBLEMON:*LEADER ", "*", "gym:*/brock", "cobblemon:**leader**")) {
            val document = parse("""{"id":"music","rules":[{"type":"trainer","match":"$pattern","sound":["test:theme"]}]}""")
            assertTrue(document.diagnostics.isEmpty(), "$pattern: ${document.diagnostics}")
            assertEquals(pattern.trim().lowercase(), document.rules.single().match)
        }
        val document = parse("""{"id":"music","rules":[{"type":"trainer","match":"cobblemon:*leader*","sound":["test:theme"]}]}""")
        val context = inceris.custommusic.battle.BattleContext(java.util.UUID(0, 1), inceris.custommusic.battle.BattleKind.TRAINER,
            trainers = listOf(inceris.custommusic.battle.TrainerIdentity("cobblemon:bug_leader", "Bug Leader")))
        assertEquals(document.rules.single(), RuleMatcher().select(context, RuleSnapshot(document.rules)).winner?.rule)
    }

    @Test fun `trainer wildcard validation rejects unsupported syntax and other selector types`() {
        for (pattern in listOf("leader?", "[leader]", "leader|boss", "gym::leader*", "gym/*/", "bug leader*")) {
            val document = parse("""{"id":"music","rules":[{"type":"trainer","match":"$pattern","sound":["test:theme"]}]}""")
            assertTrue(document.rules.isEmpty(), pattern)
            assertEquals(1, document.diagnostics.size, pattern)
        }
        for (type in listOf("species", "category", "player")) {
            val document = parse("""{"id":"music","rules":[{"type":"$type","match":"*leader*","sound":["test:theme"]}]}""")
            assertTrue(document.rules.isEmpty(), type)
            assertEquals(1, document.diagnostics.size, type)
        }
    }

    @Test fun `empty scalar missing and invalid sound arrays are rejected`() {
        for (sound in listOf("[]", "\"test:old_scalar\"", "null", "42", "{}", "[42]", "[null]",
            "[true]", "[[\"test:nested\"]]", "[\"\"]", "[\"INVALID:SOUND\"]", "[\"test:valid\",\"invalid\"]")) {
            val result = parse("""{"id":"music","rules":[{"type":"trainer","match":"gym","sound":$sound}]}""")
            assertTrue(result.rules.isEmpty(), sound)
            assertEquals(1, result.diagnostics.size, sound)
            assertTrue(result.diagnostics.single().message.contains("sound"), sound)
        }
        val missing = parse("""{"id":"music","rules":[{"type":"trainer","match":"gym"}]}""")
        assertTrue(missing.rules.isEmpty())
        assertEquals(1, missing.diagnostics.size)
    }

    @Test fun `document priorities are inherited and rule priority replaces them`() {
        val document = parse("""{"id":"music","priority":100,"rules":[
            {"type":"trainer","match":"GYM:KANTO/BROCK","sound":["test:brock"]},
            {"type":"category","match":"legendary","sound":["test:legendary"],"priority":-1}
        ]}""")
        assertTrue(document.diagnostics.isEmpty())
        assertEquals(listOf(100, -1), document.rules.map { it.priority })
        assertEquals("gym:kanto/brock", document.rules[0].match)
        assertEquals(RuleScope.TRAINER, document.rules[0].scope)
        assertEquals(RuleScope.WILD, document.rules[1].scope)
    }

    @Test fun `invalid entries are skipped without discarding valid siblings`() {
        val document = parse("""{"id":"music","rules":[
            {"type":"species","match":"mewtwo","sound":["test:bad"]},
            {"type":"unknown","match":"gym","sound":["test:bad"]},
            {"type":"trainer","match":"gym","sound":["INVALID:SOUND"]},
            {"type":"category","match":"legendary","sound":["test:ok"]}
        ]}""")
        assertEquals(1, document.rules.size)
        assertEquals(3, document.diagnostics.size)
        assertEquals(3, document.rules.single().index)
    }

    @Test fun `invalid documents never partially apply`() {
        for (json in listOf("{", "[]", "null", """{"id":"test","rules":null}""",
            """{"id":"test","priorty":100,"rules":[]}""", """{"id":"test","priority":1.5,"rules":[]}""",
            """{"id":"test","priority":2147483648,"rules":[]}""")) {
            val document = parse(json)
            assertTrue(document.rules.isEmpty(), json)
            assertTrue(document.diagnostics.isNotEmpty(), json)
        }
    }

    @Test fun `invalid field types form and scopes are rejected`() {
        for (fields in listOf("\"form\":\"normal\"", "\"scope\":\"any\"", "\"priority\":\"100\"", "\"scope\":\"wild\"", "\"unexpected\":true")) {
            val result = parse("""{"id":"test","rules":[{"type":"trainer","match":"gym","sound":["test:theme"],$fields}]}""")
            assertTrue(result.rules.isEmpty(), fields)
            assertEquals(1, result.diagnostics.size)
        }
    }

    @Test fun `custom categories accept species and form objects`() {
        val document = parse("""{"id":"test","rules":[],"categories":{"BOSS":["cobblemon:mewtwo",{"species":"cobblemon:charizard","form":"MegaX"}]}}""")
        assertTrue(document.diagnostics.isEmpty())
        assertEquals(setOf(SpeciesSelector("cobblemon:mewtwo"), SpeciesSelector("cobblemon:charizard", "megax")), document.categories["boss"])
    }

    @Test fun `duplicate equal priority selectors are reported`() {
        val rule = """{"type":"trainer","match":"gym","sound":["test:theme"]}"""
        val document = parse("""{"id":"test","rules":[$rule,$rule]}""")
        assertEquals(2, document.rules.size)
        assertTrue(document.diagnostics.single().message.contains("duplicate selector"))
    }

    @Test fun `bundled defaults parse without diagnostics and contain no active rules`() {
        val path = Path.of("src/main/resources/assets/cobblemoncustombattlemusicapi/battle_music/defaults.json")
        val document = Files.newBufferedReader(path).use { parser.parse(it, path.toString()) }
        assertTrue(document.diagnostics.isEmpty(), "${document.diagnostics}")
        assertTrue(document.rules.isEmpty())
    }

    @Test fun `builtin catalog includes legendary mythical ultra beast and form overrides`() {
        assertContains(SpeciesCategories.labels("cobblemon:mewtwo", "normal"), "legendary")
        assertContains(SpeciesCategories.labels("cobblemon:mew", null), "mythical")
        assertContains(SpeciesCategories.labels("cobblemon:nihilego", "normal"), "ultra_beast")
        assertContains(SpeciesCategories.labels("cobblemon:mewtwo", "megax"), "mega")
        assertFalse("mega" in SpeciesCategories.labels("cobblemon:mewtwo", "normal"))
        assertTrue(SpeciesCategories.labels("addon:unknown", null).isEmpty())
    }
}
