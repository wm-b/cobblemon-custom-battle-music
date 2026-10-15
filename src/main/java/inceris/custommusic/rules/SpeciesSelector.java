package inceris.custommusic.rules;

public record SpeciesSelector(String species, String form) {
    public SpeciesSelector(String species) { this(species, null); }
}
