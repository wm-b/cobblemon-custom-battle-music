package inceris.custommusic.rules;

import inceris.custommusic.battle.BattleKind;

public enum RuleScope {
  WILD,
  TRAINER,
  PLAYER,
  ANY;

  public boolean includes(BattleKind kind) {
    return this == ANY || name().equals(kind.name());
  }

  public static RuleScope defaultScope(RuleType type, RuleEvent event) {
    if (event == RuleEvent.SPAWN) return WILD;
    return switch (type) {
      case TRAINER, TRAINER_NAME -> TRAINER;
      case PLAYER -> PLAYER;
      case ALL -> ANY;
      default -> WILD;
    };
  }
}
