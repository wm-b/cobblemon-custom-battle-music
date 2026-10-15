package inceris.custommusic.rules;

import inceris.custommusic.CustomBattleMusic;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import java.util.*;
import java.util.stream.Collectors;

/** Rule configuration comes exclusively from the active client resource manager. */
public final class RuleLoader implements SimpleSynchronousResourceReloadListener {
    private RuleSnapshot snapshot = new RuleSnapshot();
    private final RuleParser parser = new RuleParser();
    private final Logger logger;

    public RuleLoader(Logger logger) { this.logger = logger; }
    public RuleSnapshot snapshot() { return snapshot; }
    @Override public ResourceLocation getFabricId() {
        return ResourceLocation.fromNamespaceAndPath(CustomBattleMusic.ASSET_NAMESPACE, "rules");
    }
    @Override public void onResourceManagerReload(ResourceManager manager) {
        List<MusicRule> rules = new ArrayList<>();
        Map<String, Set<SpeciesSelector>> categories = new LinkedHashMap<>();
        // Minecraft resolves resources at the same path in favor of the highest pack.
        new TreeMap<>(manager.listResources("battle_music", id -> id.getPath().endsWith(".json"))).forEach((id, resource) -> {
            String source = "pack:" + id;
            ParsedDocument document;
            try (var reader = resource.openAsReader()) {
                document = parser.parse(reader, source);
            } catch (Exception exception) {
                logger.warn("Unable to load {}: {}", source, exception.getMessage());
                return;
            }
            document.diagnostics().forEach(diagnostic -> logger.warn("{}: {}", diagnostic.source(), diagnostic.message()));
            rules.addAll(document.rules());
            document.categories().forEach((label, members) -> categories.computeIfAbsent(label, key -> new LinkedHashSet<>()).addAll(members));
        });
        RuleParser.duplicateSelectors(rules).forEach(group -> logger.warn("Equal-priority duplicate selectors: {}",
                group.stream().map(MusicRule::key).collect(Collectors.joining(", "))));
        snapshot = new RuleSnapshot(rules, categories);
        logger.info("Loaded {} battle music rules and {} custom categories", rules.size(), categories.size());
    }
}
