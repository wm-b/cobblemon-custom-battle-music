package inceris.custommusic.rules

import inceris.custommusic.CustomBattleMusic
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.Logger

/** Rule configuration comes exclusively from the active client resource manager. */
class RuleLoader(private val logger: Logger) : SimpleSynchronousResourceReloadListener {
    var snapshot: RuleSnapshot = RuleSnapshot()
        private set
    private val parser = RuleParser()

    override fun getFabricId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(CustomBattleMusic.ASSET_NAMESPACE, "rules")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val documents = mutableListOf<ParsedDocument>()
        // Minecraft already resolves resources at the same path in favor of the highest pack.
        manager.listResources("battle_music") { it.path.endsWith(".json") }.toSortedMap().forEach { (id, resource) ->
            readDocument("pack:$id", documents) { resource.openAsReader().use { parser.parse(it, "pack:$id") } }
        }
        val rules = documents.flatMap { it.rules }
        val categories = linkedMapOf<String, MutableSet<SpeciesSelector>>()
        documents.forEach { document -> document.categories.forEach { (label, members) ->
            categories.getOrPut(label) { linkedSetOf() }.addAll(members)
        } }
        rules.groupBy { listOf(it.type, it.match, it.form, it.scope, it.priority, it.event) }.values.filter { it.size > 1 }.forEach {
            logger.warn("Equal-priority duplicate selectors: {}", it.joinToString { rule -> rule.key })
        }
        snapshot = RuleSnapshot(rules.toList(), categories.mapValues { it.value.toSet() })
        logger.info("Loaded {} battle music rules and {} custom categories", rules.size, categories.size)
    }

    private fun readDocument(source: String, documents: MutableList<ParsedDocument>, read: () -> ParsedDocument) {
        try {
            val document = read()
            document.diagnostics.forEach { logger.warn("{}: {}", it.source, it.message) }
            documents += document
        } catch (exception: Exception) {
            logger.warn("Unable to load {}: {}", source, exception.message)
        }
    }
}
