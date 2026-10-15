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

class MusicProgramTest {
  private final MusicRule rule =
      new MusicRule(
          "test",
          "music",
          0,
          RuleType.ALL,
          "",
          List.of("test:main", "test:other"),
          0,
          null,
          RuleScope.ANY,
          RuleEvent.BATTLE,
          List.of("test:intro"),
          List.of("test:low", "test:low_other"),
          List.of("test:enemy", "test:enemy_other"));

  private ProgramTrack select(MusicProgram program) {
    return select(program, rule, false, false);
  }

  private ProgramTrack select(MusicProgram program, MusicRule rule, boolean ally, boolean enemy) {
    return program.select(rule, ally, enemy, sound -> true);
  }

  @Test
  @DisplayName("intro plays once before main and survives unchanged rules")
  void testIntroPlaysOnceBeforeMainAndSurvivesUnchangedRules() throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(new ProgramTrack("test:intro", MusicStage.INTRO), select(program));
    assertFalse(program.desired().looping());
    for (int i = 0; i < 10; i++) {
      program.observeIntro(true);
      assertEquals(
          MusicStage.INTRO,
          select(program, copy(rule, r -> r.priority = 100), false, false).stage());
    }
    program.observeIntro(false);
    assertEquals(new ProgramTrack("test:main", MusicStage.MAIN), select(program));
    assertTrue(program.desired().looping());
    select(program, null, false, false);
    assertEquals(MusicStage.MAIN, select(program).stage());
    program.clear();
    assertEquals(MusicStage.INTRO, select(program).stage());
  }

  @Test
  @DisplayName("either low HP stage interrupts intro and tracks stay stable through side changes")
  void testEitherLowHPStageInterruptsIntroAndTracksStayStableThroughSideChanges() throws Exception {
    var draws = new AtomicInteger();
    var program =
        new MusicProgram(
            bound -> {
              draws.incrementAndGet();
              return bound - 1;
            });
    select(program);
    assertEquals(
        new ProgramTrack("test:enemy_other", MusicStage.LOW_ENEMY_HP),
        select(program, rule, false, true));
    assertEquals(
        new ProgramTrack("test:low_other", MusicStage.LOW_ALLY_HP),
        select(program, rule, true, true));
    assertEquals("test:other", select(program).sound());
    for (int i = 0; i < 100; i++) {
      assertEquals("test:low_other", select(program, rule, true, false).sound());
      assertEquals("test:enemy_other", select(program, rule, false, true).sound());
      assertEquals("test:other", select(program).sound());
    }
    assertEquals(4, draws.get());
  }

  @Test
  @DisplayName("missing optional tracks fall back and missing main uses native theme")
  void testMissingOptionalTracksFallBackAndMissingMainUsesNativeTheme() throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(
        MusicStage.MAIN,
        program.select(rule, true, true, sound -> sound.equals("test:main")).stage());
    assertNull(program.select(rule, false, false, sound -> false));
    assertEquals(MusicStage.MAIN, select(program).stage());
  }

  @Test
  @DisplayName("intro waits for audio startup and proceeds if playback never starts")
  void testIntroWaitsForAudioStartupAndProceedsIfPlaybackNeverStarts() throws Exception {
    var program = new MusicProgram(bound -> 0);
    select(program);
    for (int i = 0; i < 99; i++) {
      program.observeIntro(false);
      assertEquals(MusicStage.INTRO, select(program).stage());
    }
    program.observeIntro(false);
    assertEquals(MusicStage.MAIN, select(program).stage());
  }

  @Test
  @DisplayName("changing tracks or winning rule starts its own intro")
  void testChangingTracksOrWinningRuleStartsItsOwnIntro() throws Exception {
    var program = new MusicProgram(bound -> 0);
    select(program);
    program.observeIntro(true);
    program.observeIntro(false);
    assertEquals(
        MusicStage.INTRO,
        select(program, copy(rule, r -> r.intro = List.of("test:new_intro")), false, false)
            .stage());
    assertEquals(
        MusicStage.INTRO, select(program, copy(rule, r -> r.index = 1), false, false).stage());
  }

  @Test
  @DisplayName("low HP at battle start skips intro and default rules loop normally")
  void testLowHPAtBattleStartSkipsIntroAndDefaultRulesLoopNormally() throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(MusicStage.LOW_ALLY_HP, select(program, rule, true, false).stage());
    assertEquals(MusicStage.MAIN, select(program).stage());
    assertEquals(
        MusicStage.MAIN,
        select(
                program,
                copy(
                    rule,
                    r -> {
                      r.intro = List.of();
                      r.lowAllyHp = List.of();
                      r.lowEnemyHp = List.of();
                    }),
                false,
                false)
            .stage());
  }

  @Test
  @DisplayName(
      "ally stage has priority while missing or unconfigured ally tracks allow enemy stage")
  void testAllyStageHasPriorityWhileMissingOrUnconfiguredAllyTracksAllowEnemyStage()
      throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(
        new ProgramTrack("test:low", MusicStage.LOW_ALLY_HP), select(program, rule, true, true));
    assertEquals(
        new ProgramTrack("test:enemy", MusicStage.LOW_ENEMY_HP),
        program.select(rule, true, true, sound -> !sound.equals("test:low")));
    assertEquals(
        new ProgramTrack("test:enemy", MusicStage.LOW_ENEMY_HP),
        select(program, copy(rule, r -> r.lowAllyHp = List.of()), true, true));
    assertEquals(
        MusicStage.MAIN,
        select(
                program,
                copy(
                    rule,
                    r -> {
                      r.intro = List.of();
                      r.lowEnemyHp = List.of();
                    }),
                false,
                true)
            .stage());
  }

  @Test
  @DisplayName("missing enemy track preserves intro when ally stage does not trigger")
  void testMissingEnemyTrackPreservesIntroWhenAllyStageDoesNotTrigger() throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(
        MusicStage.INTRO,
        program.select(rule, false, true, sound -> !sound.equals("test:enemy")).stage());
    assertEquals(
        MusicStage.LOW_ALLY_HP,
        program.select(rule, true, true, sound -> !sound.equals("test:enemy")).stage());
  }

  @Test
  @DisplayName("enemy low HP at battle start skips intro and clear resets both stages")
  void testEnemyLowHPAtBattleStartSkipsIntroAndClearResetsBothStages() throws Exception {
    var program = new MusicProgram(bound -> 0);
    assertEquals(MusicStage.LOW_ENEMY_HP, select(program, rule, false, true).stage());
    assertEquals(MusicStage.MAIN, select(program).stage());
    program.clear();
    assertEquals(MusicStage.INTRO, select(program).stage());
  }

  @Test
  @DisplayName("editing one low HP track list preserves other selections")
  void testEditingOneLowHPTrackListPreservesOtherSelections() throws Exception {
    var draws = new AtomicInteger();
    var program =
        new MusicProgram(
            bound -> {
              draws.incrementAndGet();
              return bound - 1;
            });
    select(program, rule, true, true);
    var changed = copy(rule, r -> r.lowEnemyHp = List.of("test:new_enemy"));
    assertEquals(
        new ProgramTrack("test:low_other", MusicStage.LOW_ALLY_HP),
        select(program, changed, true, false));
    assertEquals(
        new ProgramTrack("test:new_enemy", MusicStage.LOW_ENEMY_HP),
        select(program, changed, false, true));
    assertEquals(5, draws.get());
  }
}
