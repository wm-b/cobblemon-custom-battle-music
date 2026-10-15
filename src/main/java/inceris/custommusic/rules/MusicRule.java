package inceris.custommusic.rules;

import java.util.List;

public record MusicRule(
    String source,
    String documentId,
    int index,
    RuleType type,
    String match,
    List<String> sound,
    int priority,
    String form,
    RuleScope scope,
    RuleEvent event,
    List<String> intro,
    List<String> lowAllyHp,
    List<String> lowEnemyHp) {
  public MusicRule {
    sound = List.copyOf(sound);
    intro = List.copyOf(intro);
    lowAllyHp = List.copyOf(lowAllyHp);
    lowEnemyHp = List.copyOf(lowEnemyHp);
    if (sound.isEmpty())
      throw new IllegalArgumentException("A music rule must contain at least one sound");
  }

  public MusicRule(
      String source,
      String documentId,
      int index,
      RuleType type,
      String match,
      List<String> sound) {
    this(
        source,
        documentId,
        index,
        type,
        match,
        sound,
        0,
        null,
        RuleScope.defaultScope(type, RuleEvent.BATTLE),
        RuleEvent.BATTLE,
        List.of(),
        List.of(),
        List.of());
  }

  public String key() {
    return source + "#" + documentId + "[" + index + "]";
  }
}
