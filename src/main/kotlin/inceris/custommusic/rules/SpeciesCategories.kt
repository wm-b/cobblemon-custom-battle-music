package inceris.custommusic.rules

import com.google.gson.JsonParser

/** Cobblemon does not transmit species/form labels. This catalog describes its pinned vanilla data. */
object SpeciesCategories {
    private val catalog by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/assets/cobblemoncustombattlemusicapi/species_categories.json"))
        stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }

    fun labels(species: String, form: String?): Set<String> {
        val entry = catalog.getAsJsonObject(normalize(species)) ?: return emptySet()
        val labels = form?.let { entry.getAsJsonArray(normalize(it)) } ?: entry.getAsJsonArray("normal") ?: return emptySet()
        return labels.map { it.asString }.toSet()
    }
}
