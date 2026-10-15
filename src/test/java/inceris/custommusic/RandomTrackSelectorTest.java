package inceris.custommusic;

import static inceris.custommusic.TestFixtures.copy;
import static org.junit.jupiter.api.Assertions.*;

import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandomTrackSelectorTest {
  private final MusicRule rule =
      new MusicRule(
          "test.json",
          "music",
          0,
          RuleType.TRAINER,
          "gym",
          List.of("test:first", "test:second", "test:third"));

  @Test
  @DisplayName("every configured track is eligible and selection uses the full array")
  void testEveryConfiguredTrackIsEligibleAndSelectionUsesTheFullArray() throws Exception {
    for (int i = 0; i < rule.sound().size(); i++) {
      final int index = i;
      var selector =
          new RandomTrackSelector(
              bound -> {
                assertEquals(rule.sound().size(), bound);
                return index;
              });
      assertEquals(rule.sound().get(i), selector.select(rule));
    }
  }

  @Test
  @DisplayName("ticks repeat packets and reloads with unchanged tracks do not reroll")
  void testTicksRepeatPacketsAndReloadsWithUnchangedTracksDoNotReroll() throws Exception {
    var draws = new AtomicInteger();
    var selector =
        new RandomTrackSelector(
            bound -> {
              draws.incrementAndGet();
              return 1;
            });
    assertEquals("test:second", selector.select(rule));
    for (int i = 0; i < 100; i++) assertEquals("test:second", selector.select(copy(rule, r -> {})));
    assertEquals("test:second", selector.select(copy(rule, r -> r.priority = 200)));
    assertEquals(1, draws.get());
  }

  @Test
  @DisplayName("a new winning rule draws another track even with the same array")
  void testANewWinningRuleDrawsAnotherTrackEvenWithTheSameArray() throws Exception {
    var draws = new AtomicInteger();
    var selector = new RandomTrackSelector(bound -> draws.getAndIncrement() % bound);
    assertEquals("test:first", selector.select(rule));
    assertEquals("test:second", selector.select(copy(rule, r -> r.index = 1)));
    assertEquals(2, draws.get());
  }

  @Test
  @DisplayName("editing the winning track array draws from its new contents")
  void testEditingTheWinningTrackArrayDrawsFromItsNewContents() throws Exception {
    var draws = new AtomicInteger();
    var selector =
        new RandomTrackSelector(
            bound -> {
              draws.incrementAndGet();
              return bound - 1;
            });
    assertEquals("test:third", selector.select(rule));
    assertEquals(
        "test:new",
        selector.select(copy(rule, r -> r.sound = List.of("test:replacement", "test:new"))));
    assertEquals(2, draws.get());
  }

  @Test
  @DisplayName("ending a battle clears the choice so the next battle draws again")
  void testEndingABattleClearsTheChoiceSoTheNextBattleDrawsAgain() throws Exception {
    var draws = new AtomicInteger();
    var selector = new RandomTrackSelector(bound -> draws.getAndIncrement() % bound);
    assertEquals("test:first", selector.select(rule));
    selector.clear();
    assertEquals("test:second", selector.select(rule));
    assertEquals(2, draws.get());
  }

  @Test
  @DisplayName("losing the match resets selection without consuming randomness for fallback")
  void testLosingTheMatchResetsSelectionWithoutConsumingRandomnessForFallback() throws Exception {
    var draws = new AtomicInteger();
    var selector = new RandomTrackSelector(bound -> draws.getAndIncrement() % bound);
    assertNull(selector.select(null));
    assertEquals("test:first", selector.select(rule));
    assertNull(selector.select(null));
    assertNull(selector.select(null));
    assertEquals("test:second", selector.select(rule));
    assertEquals(2, draws.get());
  }

  @Test
  @DisplayName("a one track array always selects that track")
  void testAOneTrackArrayAlwaysSelectsThatTrack() throws Exception {
    var selector = new RandomTrackSelector();
    var single = copy(rule, r -> r.sound = List.of("test:only"));
    for (int i = 0; i < 10; i++) {
      selector.clear();
      assertEquals("test:only", selector.select(single));
    }
  }
}
