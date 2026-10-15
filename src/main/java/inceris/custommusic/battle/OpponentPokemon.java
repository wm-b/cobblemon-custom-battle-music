package inceris.custommusic.battle;

import java.util.Set;

public record OpponentPokemon(String species, String form, Set<String> categories) {
  public OpponentPokemon {
    categories = Set.copyOf(categories);
  }

  public OpponentPokemon(String species) {
    this(species, null, Set.of());
  }

  public OpponentPokemon(String species, String form) {
    this(species, form, Set.of());
  }
}
