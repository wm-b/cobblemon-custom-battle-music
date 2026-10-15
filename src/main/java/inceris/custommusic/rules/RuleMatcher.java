package inceris.custommusic.rules;

import static inceris.custommusic.rules.RuleStrings.normalize;

import inceris.custommusic.battle.*;
import java.util.*;
import net.minecraft.ChatFormatting;

/** Pure resolver. Stable source keys break ties independently of load/opponent order. */
public final class RuleMatcher {
  public RuleSelection select(BattleContext context, RuleSnapshot snapshot) {
    return select(context, snapshot, RuleEvent.BATTLE);
  }

  public RuleSelection select(BattleContext context, RuleSnapshot snapshot, RuleEvent event) {
    if (context.spectating()) return new RuleSelection(null, List.of());
    List<RuleMatch> matches = new ArrayList<>();
    for (MusicRule rule : snapshot.rules()) {
      if (rule.event() != event || !rule.scope().includes(context.kind())) continue;
      MatchRank rank = rank(rule, context, snapshot);
      if (rank != null) matches.add(new RuleMatch(rule, rank));
    }
    matches.sort(
        Comparator.<RuleMatch>comparingInt(match -> match.rank().priority())
            .reversed()
            .thenComparing(
                Comparator.comparingInt((RuleMatch match) -> match.rank().specificity()).reversed())
            .thenComparing(
                Comparator.comparingInt((RuleMatch match) -> match.rank().literalLength())
                    .reversed())
            .thenComparing(match -> match.rule().key()));
    RuleMatch winner = matches.isEmpty() ? null : matches.getFirst();
    return new RuleSelection(
        winner,
        winner == null
            ? List.of()
            : matches.stream().filter(match -> match.rank().equals(winner.rank())).toList());
  }

  private MatchRank rank(MusicRule rule, BattleContext context, RuleSnapshot snapshot) {
    String selector =
        rule.type() == RuleType.TRAINER_NAME
            ? normalizeTrainerName(rule.match())
            : normalize(rule.match());
    int specificity;
    switch (rule.type()) {
      case TRAINER -> {
        List<String> ids =
            context.trainers().stream()
                .map(TrainerIdentity::id)
                .filter(Objects::nonNull)
                .map(RuleStrings::normalize)
                .toList();
        if (selector.contains("*"))
          return ids.stream().anyMatch(id -> matchesTrainerWildcard(selector, id))
              ? new MatchRank(
                  rule.priority(), 5, (int) selector.chars().filter(c -> c != '*').count())
              : null;
        if (ids.contains(selector)) return new MatchRank(rule.priority(), 7);
        return ids.stream().anyMatch(id -> matchesTrainerPrefix(selector, id))
            ? new MatchRank(rule.priority(), 6, selector.length())
            : null;
      }
      case TRAINER_NAME -> {
        if (context.trainers().stream()
            .noneMatch(trainer -> normalizeTrainerName(trainer.name()).equals(selector)))
          return null;
        specificity = 7;
      }
      case SPECIES -> {
        if (context.opponents().stream()
            .noneMatch(
                pokemon -> matchesSpecies(new SpeciesSelector(selector, rule.form()), pokemon)))
          return null;
        specificity = rule.form() == null ? 3 : 4;
      }
      case CATEGORY -> {
        if (context.opponents().stream()
            .noneMatch(
                pokemon ->
                    pokemon.categories().stream()
                            .anyMatch(label -> normalize(label).equals(selector))
                        || snapshot.categories().getOrDefault(selector, Set.of()).stream()
                            .anyMatch(member -> matchesSpecies(member, pokemon)))) return null;
        specificity = 2;
      }
      case WILD -> {
        if (context.kind() != BattleKind.WILD) return null;
        specificity = 1;
      }
      case ALL -> specificity = 0;
      case PLAYER -> {
        if (context.kind() != BattleKind.PLAYER) return null;
        if (selector.isEmpty()) specificity = 1;
        else if (context.players().stream()
            .anyMatch(player -> player.uuid().toString().equals(selector))) specificity = 8;
        else if (context.players().stream()
            .anyMatch(
                player ->
                    player.username() != null && normalize(player.username()).equals(selector)))
          specificity = 7;
        else return null;
      }
      default -> throw new AssertionError(rule.type());
    }
    return new MatchRank(rule.priority(), specificity);
  }

  private static String normalizeTrainerName(String name) {
    return normalize(Objects.requireNonNullElse(ChatFormatting.stripFormatting(name), ""));
  }

  /** Full-ID glob: only '*' is special, and it can cross namespace/path separators. */
  private static boolean matchesTrainerWildcard(String pattern, String identity) {
    int patternIndex = 0, identityIndex = 0, lastStar = -1, retryIndex = 0;
    while (identityIndex < identity.length()) {
      if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
        lastStar = patternIndex++;
        retryIndex = identityIndex;
      } else if (patternIndex < pattern.length()
          && pattern.charAt(patternIndex) == identity.charAt(identityIndex)) {
        patternIndex++;
        identityIndex++;
      } else if (lastStar >= 0) {
        patternIndex = lastStar + 1;
        identityIndex = ++retryIndex;
      } else return false;
    }
    while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') patternIndex++;
    return patternIndex == pattern.length();
  }

  public static boolean matchesTrainerPrefix(String selector, String identity) {
    String prefix = normalize(selector), id = normalize(identity);
    return !prefix.isEmpty()
        && (id.equals(prefix)
            || (id.startsWith(prefix)
                && id.length() > prefix.length()
                && (id.charAt(prefix.length()) == ':' || id.charAt(prefix.length()) == '/')));
  }

  public static boolean matchesSpecies(SpeciesSelector selector, OpponentPokemon pokemon) {
    return normalize(selector.species()).equals(normalize(pokemon.species()))
        && (selector.form() == null
            || (pokemon.form() != null
                && normalize(selector.form()).equals(normalize(pokemon.form()))));
  }
}
