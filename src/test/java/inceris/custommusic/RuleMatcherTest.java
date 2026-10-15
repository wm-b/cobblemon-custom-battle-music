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

class RuleMatcherTest {
    private final RuleMatcher matcher = new RuleMatcher();
    private final UUID id = new UUID(0, 1);
    private BattleContext trainer() { return trainer("gym:kanto/brock", "Brock"); }
    private BattleContext trainer(String identity) { return trainer(identity, "Brock"); }
    private BattleContext trainer(String identity, String name) {
        return new BattleContext(id, BattleKind.TRAINER, List.of(new TrainerIdentity(identity, name)),
                List.of(new OpponentPokemon("cobblemon:mewtwo", "normal", Set.of("legendary"))), false, List.of(), false, false);
    }
    private BattleContext wild(OpponentPokemon... pokemon) {
        return new BattleContext(id, BattleKind.WILD, List.of(), List.of(pokemon), false, List.of(), false, false);
    }
    private MusicRule rule(RuleType type, String match) {
        return new MusicRule(match, "test", 0, type, match, List.of("test:theme"));
    }
    private MusicRule winner(BattleContext context, MusicRule... rules) {
        var winner = matcher.select(context, new RuleSnapshot(List.of(rules))).winner();
        return winner == null ? null : winner.rule();
    }

    @Test
    @DisplayName("exact trainer beats longest prefix which beats broad prefix")
    void testExactTrainerBeatsLongestPrefixWhichBeatsBroadPrefix() throws Exception {
        var broad = rule(RuleType.TRAINER, "gym"); var region = rule(RuleType.TRAINER, "gym:kanto"); var exact = rule(RuleType.TRAINER, "gym:kanto/brock");
            assertEquals(exact, winner(trainer(), broad, region, exact));
            assertEquals(region, winner(trainer(), broad, region)); assertEquals(broad, winner(trainer(), broad));
    }

    @Test
    @DisplayName("trainer prefixes respect colon and slash boundaries")
    void testTrainerPrefixesRespectColonAndSlashBoundaries() throws Exception {
        assertFalse(RuleMatcher.matchesTrainerPrefix("gym", "gymnasium:kanto/brock"));
            assertFalse(RuleMatcher.matchesTrainerPrefix("gym:kanto", "gym:kantonia/brock"));
            assertFalse(RuleMatcher.matchesTrainerPrefix("gym:johto", "gym:kanto/brock"));
            assertFalse(RuleMatcher.matchesTrainerPrefix("", "gym:kanto/brock"));
            assertTrue(RuleMatcher.matchesTrainerPrefix(" GYM:KANTO ", "gym:kanto/brock"));
            assertTrue(RuleMatcher.matchesTrainerPrefix("gym:kanto/brock", "gym:kanto/brock"));
    }

    @Test
    @DisplayName("trainer wildcards match full IDs with zero or more characters")
    void testTrainerWildcardsMatchFullIDsWithZeroOrMoreCharacters() throws Exception {
        record Case(String pattern, String identity, boolean matches) {}
            var cases = List.of(
                new Case("*leader*", "cobblemon:bug_leader", true),
                new Case(" COBBLEMON:*LEADER* ", "Cobblemon:bug_leader_elite", true),
                new Case("cobblemon:*leader", "cobblemon:leader", true),
                new Case("cobblemon:*leader", "cobblemon:bug_leader", true),
                new Case("cobblemon:*leader", "cobblemon:bug_leader_elite", false),
                new Case("cobblemon:*leader", "addon:bug_leader", false),
                new Case("*leader*", "cobblemon:bug_trainer", false),
                new Case("gym:*", "gym:kanto/brock", true), new Case("*", "gym:kanto/brock", true),
                new Case("cobblemon:**bug*leader**", "cobblemon:bug_leader", true),
                new Case("cobblemon:*bug*leader", "cobblemon:bug_bug_leader", true),
                new Case("cobblemon:*bug.leader", "cobblemon:bug_leader", false),
                new Case("cobblemon:*bug.leader", "cobblemon:bug.leader", true),
                new Case("cobblemon:bug_leader*", "cobblemon:bug_leader/veteran", true));
            for (var row : cases) {
                var rule = rule(RuleType.TRAINER, row.pattern());
                assertEquals(row.matches() ? rule : null, winner(trainer(row.identity()), rule), row.pattern() + " against " + row.identity());
            }
            assertNull(winner(trainer(null), rule(RuleType.TRAINER, "*")));
    }

    @Test
    @DisplayName("trainer wildcard ranking respects exact prefix literal length and priority")
    void testTrainerWildcardRankingRespectsExactPrefixLiteralLengthAndPriority() throws Exception {
        var context = trainer("cobblemon:bug_leader");
            var broad = rule(RuleType.TRAINER, "*"); var wildcard = rule(RuleType.TRAINER, "*leader*");
            var specific = rule(RuleType.TRAINER, "cobblemon:*leader*"); var prefix = rule(RuleType.TRAINER, "cobblemon");
            var exact = rule(RuleType.TRAINER, "cobblemon:bug_leader");
            assertEquals(exact, winner(context, specific, prefix, exact)); assertEquals(prefix, winner(context, specific, prefix));
            assertEquals(specific, winner(context, broad, wildcard, specific)); assertEquals(wildcard, winner(context, broad, wildcard));
            var higherPriority = copy(broad, r -> r.priority = 1);
            assertEquals(higherPriority, winner(context, exact, higherPriority));
            assertEquals(broad, winner(context, broad, copy(rule(RuleType.SPECIES, "cobblemon:mewtwo"), r -> r.scope = RuleScope.ANY)));
            var name = rule(RuleType.TRAINER_NAME, "Brock"); assertEquals(name, winner(context, specific, name));
    }

    @Test
    @DisplayName("equivalent wildcard ranks use stable keys independent of input order")
    void testEquivalentWildcardRanksUseStableKeysIndependentOfInputOrder() throws Exception {
        var context = trainer("cobblemon:bug_leader");
            var first = copy(rule(RuleType.TRAINER, "*leader"), r -> r.source = "a");
            var second = copy(rule(RuleType.TRAINER, "*leader*"), r -> r.source = "b");
            var selection = matcher.select(context, new RuleSnapshot(List.of(first, second)));
            assertEquals(first, selection.winner().rule()); assertEquals(2, selection.tied().size());
            assertEquals(selection, matcher.select(context, new RuleSnapshot(List.of(second, first))));
    }

    @Test
    @DisplayName("higher explicit priority overrides exact identity")
    void testHigherExplicitPriorityOverridesExactIdentity() throws Exception {
        var broad = copy(rule(RuleType.TRAINER, "gym"), r -> r.priority = 101);
            assertEquals(broad, winner(trainer(), copy(rule(RuleType.TRAINER, "gym:kanto/brock"), r -> r.priority = 100), broad));
    }

    @Test
    @DisplayName("normalization is independent of Turkish locale")
    void testNormalizationIsIndependentOfTurkishLocale() throws Exception {
        var previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                var rule = rule(RuleType.TRAINER, "GYM:KANTO"); assertEquals(rule, winner(trainer("GYM:KANTO/BROCK"), rule));
            assertEquals(rule, winner(trainer("\u00a0GYM:KANTO/BROCK\u00a0"), rule));
            } finally { Locale.setDefault(previous); }
    }

    @Test
    @DisplayName("display name is exact and available without native id")
    void testDisplayNameIsExactAndAvailableWithoutNativeId() throws Exception {
        var name = rule(RuleType.TRAINER_NAME, "brock");
            assertEquals(name, winner(trainer(null, "BROCK"), name)); assertNull(winner(trainer(null, "Brock Jr"), name));
    }

    @Test
    @DisplayName("trainer names ignore legacy colors styles and resets before normalization")
    void testTrainerNamesIgnoreLegacyColorsStylesAndResetsBeforeNormalization() throws Exception {
        var name = rule(RuleType.TRAINER_NAME, "Brock");
            for (char code : "0123456789abcdefklmnorABCDEFKLMNOR".toCharArray()) {
                assertEquals(name, winner(trainer(null, "\u00a7" + code + "  BR\u00a7rOCK  \u00a7r"), name), "Formatting code " + code);
            }
            assertEquals(name, winner(trainer(null, "\u00a76\u00a7lBrock\u00a7r"), name));
            assertNull(winner(trainer(null, "\u00a76Brock\u00a7r Jr"), name));
    }

    @Test
    @DisplayName("formatted trainer name selectors match plain names")
    void testFormattedTrainerNameSelectorsMatchPlainNames() throws Exception {
        var name = rule(RuleType.TRAINER_NAME, "\u00a7a\u00a7l Brock \u00a7r");
            assertEquals(name, winner(trainer(null, "BROCK"), name));
    }

    @Test
    @DisplayName("trainer names preserve punctuation unicode and literal section signs")
    void testTrainerNamesPreservePunctuationUnicodeAndLiteralSectionSigns() throws Exception {
        var name = rule(RuleType.TRAINER_NAME, "L\u00e9a & Co. \u00a7!");
            assertEquals(name, winner(trainer(null, "\u00a7dL\u00e9a & Co. \u00a7!\u00a7r"), name));
            assertNull(winner(trainer(null, "Lea & Co. !"), name));
    }

    @Test
    @DisplayName("exact name and exact id share a deterministic rank")
    void testExactNameAndExactIdShareADeterministicRank() throws Exception {
        var name = copy(rule(RuleType.TRAINER_NAME, "brock"), r -> r.source = "b");
            var exact = copy(rule(RuleType.TRAINER, "gym:kanto/brock"), r -> r.source = "a");
            var selection = matcher.select(trainer(), new RuleSnapshot(List.of(name, exact)));
            assertEquals(exact, selection.winner().rule()); assertEquals(2, selection.tied().size());
    }

    @Test
    @DisplayName("form beats species which beats category")
    void testFormBeatsSpeciesWhichBeatsCategory() throws Exception {
        var battle = wild(new OpponentPokemon("cobblemon:mewtwo", "megax", Set.of("legendary")));
            var category = rule(RuleType.CATEGORY, "legendary"); var species = rule(RuleType.SPECIES, "cobblemon:mewtwo");
            var form = copy(species, r -> { r.form = "MegaX"; r.source = "form"; });
            assertEquals(form, winner(battle, category, species, form)); assertEquals(species, winner(battle, category, species));
            assertEquals(category, winner(battle, category));
            assertNull(winner(wild(new OpponentPokemon("cobblemon:mewtwo", "normal")), form));
    }

    @Test
    @DisplayName("higher category priority overrides species specificity")
    void testHigherCategoryPriorityOverridesSpeciesSpecificity() throws Exception {
        var category = copy(rule(RuleType.CATEGORY, "legendary"), r -> r.priority = 1);
            assertEquals(category, winner(wild(new OpponentPokemon("cobblemon:mewtwo", null, Set.of("legendary"))), rule(RuleType.SPECIES, "cobblemon:mewtwo"), category));
    }

    @Test
    @DisplayName("wild rules do not change trainer themes or player battles")
    void testWildRulesDoNotChangeTrainerThemesOrPlayerBattles() throws Exception {
        var species = copy(rule(RuleType.SPECIES, "cobblemon:mewtwo"), r -> r.priority = 10000);
            var category = copy(rule(RuleType.CATEGORY, "legendary"), r -> r.priority = 10000);
            assertNull(winner(trainer(), species, category));
            assertNull(winner(copy(trainer(), c -> c.kind = BattleKind.PLAYER), species, category));
            var anyScope = copy(species, r -> r.scope = RuleScope.ANY); assertEquals(anyScope, winner(trainer(), anyScope));
    }

    @Test
    @DisplayName("custom categories can target a species or form")
    void testCustomCategoriesCanTargetASpeciesOrForm() throws Exception {
        var category = rule(RuleType.CATEGORY, "boss");
            var snapshot = new RuleSnapshot(List.of(category), Map.of("boss", Set.of(new SpeciesSelector("cobblemon:mewtwo", "megax"))));
            assertEquals(category, matcher.select(wild(new OpponentPokemon("cobblemon:mewtwo", "megax")), snapshot).winner().rule());
            assertNull(matcher.select(wild(new OpponentPokemon("cobblemon:mewtwo", "normal")), snapshot).winner());
    }

    @Test
    @DisplayName("multi opponent result and ties are independent of input order")
    void testMultiOpponentResultAndTiesAreIndependentOfInputOrder() throws Exception {
        var a = copy(rule(RuleType.SPECIES, "cobblemon:mewtwo"), r -> r.source = "a");
            var b = copy(rule(RuleType.SPECIES, "cobblemon:mew"), r -> r.source = "b");
            var mew = new OpponentPokemon("cobblemon:mew"); var mewtwo = new OpponentPokemon("cobblemon:mewtwo");
            var selection = matcher.select(wild(mew, mewtwo), new RuleSnapshot(List.of(b, a)));
            assertEquals(a, selection.winner().rule());
            assertEquals(selection, matcher.select(wild(mewtwo, mew), new RuleSnapshot(List.of(a, b))));
            assertEquals(2, selection.tied().size());
    }

    @Test
    @DisplayName("no matches and spectators fall back")
    void testNoMatchesAndSpectatorsFallBack() throws Exception {
        assertNull(winner(wild(new OpponentPokemon("cobblemon:eevee")), rule(RuleType.CATEGORY, "legendary")));
            assertNull(winner(copy(trainer(), c -> c.spectating = true), rule(RuleType.TRAINER, "gym")));
            assertNull(winner(trainer()));
    }
}
