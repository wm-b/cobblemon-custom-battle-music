package inceris.custommusic.battle;

import java.util.*;

public final class PokemonTraits {
    private PokemonTraits() {}
    public static Set<String> categories(Set<String> labels, boolean shiny) {
        Set<String> result = new LinkedHashSet<>(labels);
        result.remove("shiny");
        if (shiny) result.add("shiny");
        return Set.copyOf(result);
    }
    /** Cobblemon 1.8.2's getDepletableRedGreen uses 0.2 as its pure-red boundary. */
    public static boolean isLowHealth(float hp, float maxHp, boolean flat) {
        if (!Float.isFinite(hp) || hp <= 0 || (flat && (!Float.isFinite(maxHp) || maxHp <= 0))) return false;
        float ratio = flat ? hp / maxHp : hp;
        return ratio <= 0.2f;
    }
}
