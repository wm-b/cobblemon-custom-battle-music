package inceris.custommusic;

import static inceris.custommusic.TestFixtures.copy;
import static inceris.custommusic.TestFixtures.only;
import static org.junit.jupiter.api.Assertions.*;

import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtendedRulesTest {
  private final RuleParser parser = new RuleParser();
  private final RuleMatcher matcher = new RuleMatcher();
  private final UUID id = new UUID(0, 1);

  private ParsedDocument document(String... rules) {
    return parser.parse(
        new StringReader("{\"id\":\"test\",\"rules\":[" + String.join(",", rules) + "]}"), "test");
  }

  private RuleSnapshot snapshot(String... rules) {
    var parsed = document(rules);
    assertTrue(parsed.diagnostics().isEmpty(), parsed.diagnostics().toString());
    return new RuleSnapshot(parsed.rules());
  }

  private String sound(BattleContext context, RuleSnapshot snapshot) {
    return only(matcher.select(context, snapshot).winner().rule().sound());
  }

  @Test
  @DisplayName("wild player and all defaults select by battle kind")
  void testWildPlayerAndAllDefaultsSelectByBattleKind() throws Exception {
    var rules =
        snapshot(
            "{\"type\":\"all\",\"sound\":[\"test:all\"]}",
            "{\"type\":\"wild\",\"sound\":[\"test:wild\"]}",
            "{\"type\":\"player\",\"sound\":[\"test:player\"]}");
    assertEquals("test:wild", sound(new BattleContext(id, BattleKind.WILD), rules));
    assertEquals("test:player", sound(new BattleContext(id, BattleKind.PLAYER), rules));
    assertEquals("test:all", sound(new BattleContext(id, BattleKind.TRAINER), rules));
    assertNull(
        matcher
            .select(copy(new BattleContext(id, BattleKind.PLAYER), c -> c.spectating = true), rules)
            .winner());
  }

  @Test
  @DisplayName(
      "UUID targeting outranks username then player default and tolerates missing profiles")
  void testUUIDTargetingOutranksUsernameThenPlayerDefaultAndToleratesMissingProfiles()
      throws Exception {
    var rules =
        snapshot(
            "{\"type\":\"player\",\"sound\":[\"test:default\"]}",
            "{\"type\":\"player\",\"match\":\"ASH\",\"sound\":[\"test:name\"]}",
            "{\"type\":\"player\",\"match\":\"00000000-0000-0000-0000-000000000001\",\"sound\":[\"test:uuid\"]}");
    var context =
        copy(
            new BattleContext(new UUID(0, 2), BattleKind.PLAYER),
            c -> c.players = List.of(new PlayerIdentity(id, "Ash")));
    assertEquals("test:uuid", sound(context, rules));
    assertEquals(
        "test:uuid", sound(copy(context, c -> c.players = List.of(new PlayerIdentity(id))), rules));
    assertEquals("test:name", sound(context, new RuleSnapshot(rules.rules().subList(0, 2))));
    assertEquals(
        "test:default",
        sound(
            copy(context, c -> c.players = List.of(new PlayerIdentity(new UUID(0, 3), "Ash Jr"))),
            rules));
  }

  @Test
  @DisplayName("specific species beats wild default and priority overrides specificity")
  void testSpecificSpeciesBeatsWildDefaultAndPriorityOverridesSpecificity() throws Exception {
    var rules =
        snapshot(
            "{\"type\":\"wild\",\"sound\":[\"test:wild\"]}",
            "{\"type\":\"species\",\"match\":\"cobblemon:eevee\",\"sound\":[\"test:eevee\"]}");
    var context =
        copy(
            new BattleContext(id, BattleKind.WILD),
            c -> c.opponents = List.of(new OpponentPokemon("cobblemon:eevee")));
    assertEquals(RuleType.SPECIES, matcher.select(context, rules).winner().rule().type());
    var changed =
        new RuleSnapshot(
            rules.rules().stream()
                .map(rule -> rule.type() == RuleType.WILD ? copy(rule, r -> r.priority = 1) : rule)
                .toList());
    assertEquals(RuleType.WILD, matcher.select(context, changed).winner().rule().type());
  }

  @Test
  @DisplayName("player scope isolates species rules from wild and NPC battles")
  void testPlayerScopeIsolatesSpeciesRulesFromWildAndNPCBattles() throws Exception {
    var rules =
        snapshot(
            "{\"type\":\"species\",\"match\":\"cobblemon:eevee\",\"scope\":\"player\",\"sound\":[\"test:eevee\"]}");
    var context =
        copy(
            new BattleContext(id, BattleKind.PLAYER),
            c -> c.opponents = List.of(new OpponentPokemon("cobblemon:eevee")));
    assertNotNull(matcher.select(context, rules).winner());
    assertNull(matcher.select(copy(context, c -> c.kind = BattleKind.WILD), rules).winner());
    assertNull(matcher.select(copy(context, c -> c.kind = BattleKind.TRAINER), rules).winner());
  }

  @Test
  @DisplayName("spawn rules cannot change battle music and use their own ranking")
  void testSpawnRulesCannotChangeBattleMusicAndUseTheirOwnRanking() throws Exception {
    var rules =
        snapshot(
            "{\"type\":\"all\",\"sound\":[\"test:battle\"]}",
            "{\"type\":\"wild\",\"event\":\"spawn\",\"sound\":[\"test:appearance\"]}",
            "{\"type\":\"category\",\"match\":\"shiny\",\"event\":\"spawn\",\"sound\":[\"test:shiny\"]}");
    var context =
        copy(
            new BattleContext(id, BattleKind.WILD),
            c ->
                c.opponents =
                    List.of(new OpponentPokemon("cobblemon:eevee", null, Set.of("shiny"))));
    assertEquals("test:battle", sound(context, rules));
    assertEquals(
        "test:shiny",
        only(matcher.select(context, rules, RuleEvent.SPAWN).winner().rule().sound()));
    assertEquals(RuleScope.WILD, rules.rules().getLast().scope());
  }

  @Test
  @DisplayName("new rule shapes and optional stages are strictly validated")
  void testNewRuleShapesAndOptionalStagesAreStrictlyValidated() throws Exception {
    var valid =
        document(
            "{\"type\":\"all\",\"sound\":[\"test:main\"],\"intro\":[\"test:intro\"],\"low_ally_hp\":[\"test:low\"],\"low_enemy_hp\":[\"test:enemy\"]}");
    assertTrue(valid.diagnostics().isEmpty());
    assertEquals(List.of("test:intro"), only(valid.rules()).intro());
    assertEquals(List.of("test:low"), only(valid.rules()).lowAllyHp());
    assertEquals(List.of("test:enemy"), only(valid.rules()).lowEnemyHp());
    for (String rule :
        List.of(
            "{\"type\":\"wild\",\"match\":\"anything\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"match\":\"anything\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"player\",\"match\":\"not-a-uuid-or-name\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"player\",\"scope\":\"any\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"wild\",\"scope\":\"player\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"intro\":[],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_hp\":[\"test:legacy\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_ally_hp\":\"test:scalar\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_enemy_hp\":\"test:scalar\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_ally_hp\":[],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_enemy_hp\":[],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_ally_hp\":[\"Bad:ID\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"low_enemy_hp\":[\"Bad:ID\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"intro\":[\"Bad:ID\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"player\",\"event\":\"spawn\",\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"event\":\"spawn\",\"intro\":[\"test:intro\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"event\":\"spawn\",\"low_ally_hp\":[\"test:low\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"event\":\"spawn\",\"low_enemy_hp\":[\"test:low\"],\"sound\":[\"test:x\"]}",
            "{\"type\":\"all\",\"event\":\"spawn\",\"scope\":\"any\",\"sound\":[\"test:x\"]}")) {
      var parsed = document(rule);
      assertTrue(parsed.rules().isEmpty(), rule);
      assertEquals(1, parsed.diagnostics().size(), rule);
    }
  }

  @Test
  @DisplayName("low HP tracks are independently optional")
  void testLowHPTracksAreIndependentlyOptional() throws Exception {
    var ally =
        document("{\"type\":\"wild\",\"sound\":[\"test:main\"],\"low_ally_hp\":[\"test:ally\"]}");
    var enemy =
        document("{\"type\":\"wild\",\"sound\":[\"test:main\"],\"low_enemy_hp\":[\"test:enemy\"]}");
    var neither = document("{\"type\":\"wild\",\"sound\":[\"test:main\"]}");
    assertTrue(
        ally.diagnostics().isEmpty()
            && enemy.diagnostics().isEmpty()
            && neither.diagnostics().isEmpty());
    assertEquals(List.of("test:ally"), only(ally.rules()).lowAllyHp());
    assertTrue(only(ally.rules()).lowEnemyHp().isEmpty());
    assertEquals(List.of("test:enemy"), only(enemy.rules()).lowEnemyHp());
    assertTrue(only(enemy.rules()).lowAllyHp().isEmpty());
    assertTrue(only(neither.rules()).lowAllyHp().isEmpty());
    assertTrue(only(neither.rules()).lowEnemyHp().isEmpty());
  }

  @Test
  @DisplayName("shiny membership follows individuals and cannot be overridden by a static category")
  void testShinyMembershipFollowsIndividualsAndCannotBeOverriddenByAStaticCategory()
      throws Exception {
    assertEquals(Set.of("legendary", "shiny"), PokemonTraits.categories(Set.of("legendary"), true));
    assertEquals(
        Set.of("legendary"), PokemonTraits.categories(Set.of("legendary", "shiny"), false));
    var parsed =
        parser.parse(
            new StringReader(
                "{\"id\":\"test\",\"rules\":[],\"categories\":{\"SHINY\":[\"cobblemon:eevee\"]}}"),
            "test");
    assertTrue(parsed.categories().isEmpty());
    assertEquals(1, parsed.diagnostics().size());
  }

  @Test
  @DisplayName("red HP boundary excludes fainted Pokemon and handles fractional health")
  void testRedHPBoundaryExcludesFaintedPokemonAndHandlesFractionalHealth() throws Exception {
    assertTrue(PokemonTraits.isLowHealth(20f, 100f, true));
    assertFalse(PokemonTraits.isLowHealth(20.01f, 100f, true));
    assertTrue(PokemonTraits.isLowHealth(0.2f, 0f, false));
    assertFalse(PokemonTraits.isLowHealth(0.201f, 0f, false));
    assertFalse(PokemonTraits.isLowHealth(0f, 100f, true));
    assertFalse(PokemonTraits.isLowHealth(1f, 0f, true));
    assertFalse(PokemonTraits.isLowHealth(Float.NaN, 100f, true));
  }
}
