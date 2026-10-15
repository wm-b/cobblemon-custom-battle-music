package inceris.custommusic;

import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static inceris.custommusic.TestFixtures.copy;
import static inceris.custommusic.TestFixtures.only;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.*;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

class RuleLoaderTest {
    private final RuleLoader loader = new RuleLoader(org.slf4j.LoggerFactory.getLogger(RuleLoaderTest.class));
    private final PackResources pack = (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
            new Class<?>[]{PackResources.class}, (proxy, method, args) -> switch (method.getName()) {
                case "packId", "toString" -> "testpack";
                case "close" -> null;
                default -> throw new AssertionError("Unexpected pack call: " + method.getName());
            });
    private Resource resource(String json) {
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @SafeVarargs
    private ResourceManager manager(Map.Entry<String, String>... files) {
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        for (var file : files) resources.put(ResourceLocation.parse(file.getKey()), resource(file.getValue()));
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return "test resource manager";
            if (!method.getName().equals("listResources")) throw new AssertionError("Unexpected resource manager call: " + method.getName());
            @SuppressWarnings("unchecked") var predicate = (Predicate<ResourceLocation>) args[1];
            Map<ResourceLocation, Resource> selected = new LinkedHashMap<>();
            resources.forEach((id, resource) -> { if (id.getPath().startsWith(args[0] + "/") && predicate.test(id)) selected.put(id, resource); });
            return selected;
        });
    }
    private String music(String species, String sound) {
        return "{\"id\":\"music\",\"rules\":[{\"type\":\"category\",\"match\":\"boss\",\"sound\":[\"" + sound
                + "\"]}],\"categories\":{\"boss\":[\"" + species + "\"]}}";
    }

    @Test
    @DisplayName("pack removal clears its rules and custom categories")
    void testPackRemovalClearsItsRulesAndCustomCategories() throws Exception {
        loader.onResourceManagerReload(manager(Map.entry("testpack:battle_music/music.json", "{\n            \"id\":\"music\",\"rules\":[{\"type\":\"category\",\"match\":\"boss\",\"sound\":[\"test:theme\"]}],\n            \"categories\":{\"boss\":[\"cobblemon:eevee\"]}\n        }")));
            assertEquals("pack:testpack:battle_music/music.json", only(loader.snapshot().rules()).source());
            var battle = copy(new BattleContext(new UUID(0, 1), BattleKind.WILD), c -> c.opponents = List.of(new OpponentPokemon("cobblemon:eevee")));
            assertNotNull(new RuleMatcher().select(battle, loader.snapshot()).winner());
            loader.onResourceManagerReload(manager());
            assertEquals(new RuleSnapshot(), loader.snapshot());
            assertNull(new RuleMatcher().select(battle, loader.snapshot()).winner());
    }

    @Test
    @DisplayName("pack updates replace previous rules and category membership")
    void testPackUpdatesReplacePreviousRulesAndCategoryMembership() throws Exception {
        loader.onResourceManagerReload(manager(Map.entry("testpack:battle_music/music.json", music("cobblemon:eevee", "test:old"))));
            loader.onResourceManagerReload(manager(Map.entry("testpack:battle_music/music.json", music("cobblemon:mew", "test:new"))));
            assertEquals(List.of("test:new"), only(loader.snapshot().rules()).sound());
            assertEquals(Set.of(new SpeciesSelector("cobblemon:mew")), loader.snapshot().categories().get("boss"));
    }

    @Test
    @DisplayName("independent pack rules compete by priority and malformed files do not discard valid siblings")
    void testIndependentPackRulesCompeteByPriorityAndMalformedFilesDoNotDiscardValidSiblings() throws Exception {
        loader.onResourceManagerReload(manager(
                    Map.entry("first:battle_music/music.json", "{\"id\":\"first\",\"rules\":[{\"type\":\"wild\",\"sound\":[\"test:low\"]}]}"),
                    Map.entry("second:battle_music/music.json", "{\"id\":\"second\",\"priority\":100,\"rules\":[{\"type\":\"all\",\"sound\":[\"test:high\"]}]}"),
                    Map.entry("second:battle_music/broken.json", "{broken"),
                    Map.entry("second:battle_music/notes.txt", "not JSON")));
            assertEquals(2, loader.snapshot().rules().size());
            assertTrue(loader.snapshot().rules().stream().allMatch(rule -> rule.source().startsWith("pack:")));
            var battle = new BattleContext(new UUID(0, 1), BattleKind.WILD);
            assertEquals(List.of("test:high"), new RuleMatcher().select(battle, loader.snapshot()).winner().rule().sound());
    }
}
