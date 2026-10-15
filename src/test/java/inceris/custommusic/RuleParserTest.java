package inceris.custommusic;

import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static inceris.custommusic.TestFixtures.copy;
import static inceris.custommusic.TestFixtures.only;

class RuleParserTest {
    private final RuleParser parser = new RuleParser();
    private ParsedDocument parse(String json) { return parser.parse(new StringReader(json), "test.json"); }

    @Test
    @DisplayName("sound arrays preserve all tracks and their order")
    void testSoundArraysPreserveAllTracksAndTheirOrder() throws Exception {
        var result = parse("{\"id\":\"music\",\"rules\":[\n            {\"type\":\"trainer\",\"match\":\"gym\",\"sound\":[\"test:first\",\"test:second\",\"test:third\"]}\n        ]}");
            assertTrue(result.diagnostics().isEmpty());
            assertEquals(List.of("test:first", "test:second", "test:third"), only(result.rules()).sound());
    }

    @Test
    @DisplayName("trainer wildcard rules parse and match an NPC class ID")
    void testTrainerWildcardRulesParseAndMatchAnNPCClassID() throws Exception {
        for (String pattern : List.of("*leader*", " COBBLEMON:*LEADER ", "*", "gym:*/brock", "cobblemon:**leader**")) {
                var document = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"trainer\",\"match\":\"$pattern\",\"sound\":[\"test:theme\"]}]}".replace("$pattern", pattern));
                assertTrue(document.diagnostics().isEmpty(), pattern + ": " + document.diagnostics());
                assertEquals(pattern.strip().toLowerCase(Locale.ROOT), only(document.rules()).match());
            }
            var document = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"trainer\",\"match\":\"cobblemon:*leader*\",\"sound\":[\"test:theme\"]}]}");
            var context = new BattleContext(new UUID(0, 1), BattleKind.TRAINER, List.of(new TrainerIdentity("cobblemon:bug_leader", "Bug Leader")), List.of(), false, List.of(), false, false);
            assertEquals(only(document.rules()), new RuleMatcher().select(context, new RuleSnapshot(document.rules())).winner().rule());
    }

    @Test
    @DisplayName("trainer wildcard validation rejects unsupported syntax and other selector types")
    void testTrainerWildcardValidationRejectsUnsupportedSyntaxAndOtherSelectorTypes() throws Exception {
        for (String pattern : List.of("leader?", "[leader]", "leader|boss", "gym::leader*", "gym/*/", "bug leader*")) {
                var document = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"trainer\",\"match\":\"$pattern\",\"sound\":[\"test:theme\"]}]}".replace("$pattern", pattern));
                assertTrue(document.rules().isEmpty(), pattern); assertEquals(1, document.diagnostics().size(), pattern);
            }
            for (String type : List.of("species", "category", "player")) {
                var document = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"$type\",\"match\":\"*leader*\",\"sound\":[\"test:theme\"]}]}".replace("$type", type));
                assertTrue(document.rules().isEmpty(), type); assertEquals(1, document.diagnostics().size(), type);
            }
    }

    @Test
    @DisplayName("empty scalar missing and invalid sound arrays are rejected")
    void testEmptyScalarMissingAndInvalidSoundArraysAreRejected() throws Exception {
        for (String sound : List.of("[]", "\"test:old_scalar\"", "null", "42", "{}", "[42]", "[null]", "[true]", "[[\"test:nested\"]]", "[\"\"]", "[\"INVALID:SOUND\"]", "[\"test:valid\",\"invalid\"]")) {
                var result = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"trainer\",\"match\":\"gym\",\"sound\":$sound}]}".replace("$sound", sound));
                assertTrue(result.rules().isEmpty(), sound); assertEquals(1, result.diagnostics().size(), sound);
                assertTrue(only(result.diagnostics()).message().contains("sound"), sound);
            }
            var missing = parse("{\"id\":\"music\",\"rules\":[{\"type\":\"trainer\",\"match\":\"gym\"}]}");
            assertTrue(missing.rules().isEmpty()); assertEquals(1, missing.diagnostics().size());
    }

    @Test
    @DisplayName("document priorities are inherited and rule priority replaces them")
    void testDocumentPrioritiesAreInheritedAndRulePriorityReplacesThem() throws Exception {
        var document = parse("{\"id\":\"music\",\"priority\":100,\"rules\":[\n            {\"type\":\"trainer\",\"match\":\"GYM:KANTO/BROCK\",\"sound\":[\"test:brock\"]},\n            {\"type\":\"category\",\"match\":\"legendary\",\"sound\":[\"test:legendary\"],\"priority\":-1}\n        ]}");
            assertTrue(document.diagnostics().isEmpty());
            assertEquals(List.of(100, -1), document.rules().stream().map(MusicRule::priority).toList());
            assertEquals("gym:kanto/brock", document.rules().get(0).match());
            assertEquals(RuleScope.TRAINER, document.rules().get(0).scope());
            assertEquals(RuleScope.WILD, document.rules().get(1).scope());
    }

    @Test
    @DisplayName("invalid entries are skipped without discarding valid siblings")
    void testInvalidEntriesAreSkippedWithoutDiscardingValidSiblings() throws Exception {
        var document = parse("{\"id\":\"music\",\"rules\":[\n            {\"type\":\"species\",\"match\":\"mewtwo\",\"sound\":[\"test:bad\"]},\n            {\"type\":\"unknown\",\"match\":\"gym\",\"sound\":[\"test:bad\"]},\n            {\"type\":\"trainer\",\"match\":\"gym\",\"sound\":[\"INVALID:SOUND\"]},\n            {\"type\":\"category\",\"match\":\"legendary\",\"sound\":[\"test:ok\"]}\n        ]}");
            assertEquals(1, document.rules().size()); assertEquals(3, document.diagnostics().size());
            assertEquals(3, only(document.rules()).index());
    }

    @Test
    @DisplayName("invalid documents never partially apply")
    void testInvalidDocumentsNeverPartiallyApply() throws Exception {
        for (String json : List.of("{", "[]", "null", "{\"id\":\"test\",\"rules\":null}", "{\"id\":\"test\",\"priorty\":100,\"rules\":[]}", "{\"id\":\"test\",\"priority\":1.5,\"rules\":[]}", "{\"id\":\"test\",\"priority\":2147483648,\"rules\":[]}")) {
                var document = parse(json);
                assertTrue(document.rules().isEmpty(), json); assertFalse(document.diagnostics().isEmpty(), json);
            }
    }

    @Test
    @DisplayName("invalid field types form and scopes are rejected")
    void testInvalidFieldTypesFormAndScopesAreRejected() throws Exception {
        for (String fields : List.of("\"form\":\"normal\"", "\"scope\":\"any\"", "\"priority\":\"100\"", "\"scope\":\"wild\"", "\"unexpected\":true")) {
                var result = parse("{\"id\":\"test\",\"rules\":[{\"type\":\"trainer\",\"match\":\"gym\",\"sound\":[\"test:theme\"],$fields}]}".replace("$fields", fields));
                assertTrue(result.rules().isEmpty(), fields); assertEquals(1, result.diagnostics().size());
            }
    }

    @Test
    @DisplayName("custom categories accept species and form objects")
    void testCustomCategoriesAcceptSpeciesAndFormObjects() throws Exception {
        var document = parse("{\"id\":\"test\",\"rules\":[],\"categories\":{\"BOSS\":[\"cobblemon:mewtwo\",{\"species\":\"cobblemon:charizard\",\"form\":\"MegaX\"}]}}");
            assertTrue(document.diagnostics().isEmpty());
            assertEquals(Set.of(new SpeciesSelector("cobblemon:mewtwo"), new SpeciesSelector("cobblemon:charizard", "megax")), document.categories().get("boss"));
    }

    @Test
    @DisplayName("duplicate equal priority selectors are reported")
    void testDuplicateEqualPrioritySelectorsAreReported() throws Exception {
        String rule = "{\"type\":\"trainer\",\"match\":\"gym\",\"sound\":[\"test:theme\"]}";
            var document = parse("{\"id\":\"test\",\"rules\":[$rule,$rule]}".replace("$rule", rule));
            assertEquals(2, document.rules().size());
            assertTrue(only(document.diagnostics()).message().contains("duplicate selector"));
    }

    @Test
    @DisplayName("bundled defaults parse without diagnostics and contain no active rules")
    void testBundledDefaultsParseWithoutDiagnosticsAndContainNoActiveRules() throws Exception {
        var path = Path.of("src/main/resources/assets/cobblemoncustombattlemusic/battle_music/defaults.json");
            try (var reader = Files.newBufferedReader(path)) {
                var document = parser.parse(reader, path.toString());
                assertTrue(document.diagnostics().isEmpty(), document.diagnostics().toString());
                assertTrue(document.rules().isEmpty());
            }
    }

    @Test
    @DisplayName("builtin catalog includes legendary mythical ultra beast and form overrides")
    void testBuiltinCatalogIncludesLegendaryMythicalUltraBeastAndFormOverrides() throws Exception {
        assertTrue(SpeciesCategories.labels("cobblemon:mewtwo", "normal").contains("legendary"));
            assertTrue(SpeciesCategories.labels("cobblemon:mew", null).contains("mythical"));
            assertTrue(SpeciesCategories.labels("cobblemon:nihilego", "normal").contains("ultra_beast"));
            assertTrue(SpeciesCategories.labels("cobblemon:mewtwo", "megax").contains("mega"));
            assertFalse(SpeciesCategories.labels("cobblemon:mewtwo", "normal").contains("mega"));
            assertTrue(SpeciesCategories.labels("addon:unknown", null).isEmpty());
    }
}
