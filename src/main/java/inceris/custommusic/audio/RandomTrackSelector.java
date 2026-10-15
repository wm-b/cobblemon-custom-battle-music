package inceris.custommusic.audio;

import inceris.custommusic.rules.MusicRule;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/** Pick once when a winning rule becomes active; repeated ticks/packets keep that track. */
public final class RandomTrackSelector {
  private final IntUnaryOperator chooseIndex;
  private String ruleKey;
  private List<String> sounds = List.of();
  private String track;

  public RandomTrackSelector() {
    this(bound -> ThreadLocalRandom.current().nextInt(bound));
  }

  public RandomTrackSelector(IntUnaryOperator chooseIndex) {
    this.chooseIndex = chooseIndex;
  }

  public String select(MusicRule rule) {
    return select(rule, rule == null ? List.of() : rule.sound());
  }

  public String select(MusicRule rule, List<String> candidates) {
    if (rule == null || candidates.isEmpty()) {
      clear();
      return null;
    }
    if (!Objects.equals(ruleKey, rule.key()) || !sounds.equals(candidates)) {
      ruleKey = rule.key();
      sounds = List.copyOf(candidates);
      track = sounds.get(chooseIndex.applyAsInt(sounds.size()));
    }
    return track;
  }

  public void clear() {
    ruleKey = null;
    sounds = List.of();
    track = null;
  }
}
