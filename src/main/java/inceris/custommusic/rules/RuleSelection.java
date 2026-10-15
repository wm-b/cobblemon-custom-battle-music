package inceris.custommusic.rules;

import java.util.List;

public record RuleSelection(RuleMatch winner, List<RuleMatch> tied) {
  public RuleSelection {
    tied = List.copyOf(tied);
  }
}
