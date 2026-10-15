package inceris.custommusic.rules;

import java.util.*;

public record RuleSnapshot(List<MusicRule> rules, Map<String, Set<SpeciesSelector>> categories) {
  public RuleSnapshot {
    rules = List.copyOf(rules);
    Map<String, Set<SpeciesSelector>> copy = new LinkedHashMap<>();
    categories.forEach((label, members) -> copy.put(label, Set.copyOf(members)));
    categories = Collections.unmodifiableMap(copy);
  }

  public RuleSnapshot() {
    this(List.of(), Map.of());
  }

  public RuleSnapshot(List<MusicRule> rules) {
    this(rules, Map.of());
  }
}
