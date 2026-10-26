package inceris.custommusic;

import static inceris.custommusic.TestFixtures.copy;
import static inceris.custommusic.TestFixtures.only;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.*;
import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrismaExamplesTest {
  private final Path root = Path.of("examples/prisma-tests");

  @BeforeEach
  void requireExamplePacks() {
    assumeTrue(
        Files.isDirectory(root.resolve("resourcepack"))
            || Files.isDirectory(root.resolve("variants")),
        "Optional Prisma example packs are not installed in examples/prisma-tests");
  }

  private ParsedDocument parse(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      var result = new RuleParser().parse(reader, path.toString());
      assertTrue(result.diagnostics().isEmpty(), path + ": " + result.diagnostics());
      return result;
    }
  }

  private String text(JsonObject obj, String name, String fallback) {
    return obj.has(name) ? obj.get(name).getAsString() : fallback;
  }

  @Test
  @DisplayName("complete menu scenarios select their documented battle and appearance sounds")
  void testCompleteMenuScenariosSelectTheirDocumentedBattleAndAppearanceSounds() throws Exception {
    var parsed = parse(root.resolve("resourcepack/assets/prisma_test/battle_music/music.json"));
    var snapshot = new RuleSnapshot(parsed.rules(), parsed.categories());
    JsonArray scenarios;
    try (var reader = Files.newBufferedReader(root.resolve("scenarios.json"))) {
      scenarios = JsonParser.parseReader(reader).getAsJsonArray();
    }
    var matcher = new RuleMatcher();
    for (var scenario : scenarios) {
      var row = scenario.getAsJsonObject();
      var expected = row.getAsJsonObject("expect");
      if (expected == null) continue;
      String species = text(expected, "species", "cobblemon:rattata"),
          form = text(expected, "form", "normal");
      var pokemon =
          new OpponentPokemon(
              species,
              form,
              PokemonTraits.categories(
                  SpeciesCategories.labels(species, form),
                  expected.has("shiny") && expected.get("shiny").getAsBoolean()));
      var kind = BattleKind.valueOf(text(expected, "kind", "wild").toUpperCase(Locale.ROOT));
      List<TrainerIdentity> trainers =
          kind == BattleKind.TRAINER
              ? List.of(
                  new TrainerIdentity(
                      text(expected, "trainerId", "prisma_test:default"),
                      text(expected, "trainerName", "Prisma Default Trainer")))
              : List.of();
      List<PlayerIdentity> players =
          kind == BattleKind.PLAYER
              ? List.of(
                  new PlayerIdentity(
                      UUID.fromString(
                          text(expected, "playerUuid", "00000000-0000-0000-0000-000000000042")),
                      text(expected, "playerName", null)))
              : List.of();
      var context =
          new BattleContext(
              new UUID(0, 1), kind, trainers, List.of(pokemon), false, players, false, false);
      var event = RuleEvent.valueOf(text(expected, "event", "battle").toUpperCase(Locale.ROOT));
      var winner = matcher.select(context, snapshot, event).winner();
      List<String> sounds = new ArrayList<>();
      expected.getAsJsonArray("sounds").forEach(sound -> sounds.add(sound.getAsString()));
      assertEquals(
          sounds, winner == null ? null : winner.rule().sound(), row.get("id").getAsString());
      assertNull(matcher.select(copy(context, c -> c.spectating = true), snapshot, event).winner());
    }
  }

  @Test
  @DisplayName("isolated variants parse and expose their intended selectors")
  void testIsolatedVariantsParseAndExposeTheirIntendedSelectors() throws Exception {
    assertTrue(parse(root.resolve("variants/no-rules.json")).rules().isEmpty());
    for (var row :
        List.of(Map.entry("spawn-all", RuleType.ALL), Map.entry("spawn-wild", RuleType.WILD))) {
      var rule = only(parse(root.resolve("variants/" + row.getKey() + ".json")).rules());
      assertEquals(row.getValue(), rule.type());
      assertEquals(RuleEvent.SPAWN, rule.event());
      assertEquals(RuleScope.WILD, rule.scope());
    }
    var named = parse(root.resolve("variants/player-name.json"));
    assertEquals(2, named.rules().size());
    assertTrue(named.rules().stream().allMatch(rule -> rule.type() == RuleType.PLAYER));
  }
}
