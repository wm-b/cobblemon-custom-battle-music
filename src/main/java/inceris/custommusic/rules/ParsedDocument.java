package inceris.custommusic.rules;

import java.util.*;

public record ParsedDocument(List<MusicRule> rules, Map<String, Set<SpeciesSelector>> categories, List<Diagnostic> diagnostics) {
    public ParsedDocument {
        RuleSnapshot snapshot = new RuleSnapshot(rules, categories);
        rules = snapshot.rules();
        categories = snapshot.categories();
        diagnostics = List.copyOf(diagnostics);
    }
}
