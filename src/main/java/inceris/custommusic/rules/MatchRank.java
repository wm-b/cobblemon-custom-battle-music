package inceris.custommusic.rules;

public record MatchRank(int priority, int specificity, int literalLength) {
    public MatchRank(int priority, int specificity) { this(priority, specificity, 0); }
}
