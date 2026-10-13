package inceris.custommusic.battle

object PokemonTraits {
    fun categories(labels: Set<String>, shiny: Boolean): Set<String> =
        (labels - "shiny") + if (shiny) setOf("shiny") else emptySet()

    /** Cobblemon 1.8.2's getDepletableRedGreen uses 0.2 as its pure-red boundary. */
    fun isLowHealth(hp: Float, maxHp: Float, flat: Boolean): Boolean {
        if (!hp.isFinite() || hp <= 0 || (flat && (!maxHp.isFinite() || maxHp <= 0))) return false
        val ratio = if (flat) hp / maxHp else hp
        return ratio <= 0.2f
    }
}
