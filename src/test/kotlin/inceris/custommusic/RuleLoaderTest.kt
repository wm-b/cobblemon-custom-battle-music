package inceris.custommusic

import inceris.custommusic.battle.*
import inceris.custommusic.rules.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.function.Predicate
import kotlin.test.*

/** Exercises resource reload behavior without starting Minecraft or reading a local config directory. */
class RuleLoaderTest {
    private val loader = RuleLoader(LoggerFactory.getLogger(RuleLoaderTest::class.java))
    private val pack = Proxy.newProxyInstance(PackResources::class.java.classLoader, arrayOf(PackResources::class.java)) { _, method, _ ->
        when (method.name) {
            "packId", "toString" -> "testpack"
            "close" -> null
            else -> error("Unexpected pack call: ${method.name}")
        }
    } as PackResources

    private fun resource(json: String) = Resource(pack, IoSupplier { ByteArrayInputStream(json.toByteArray()) })
    private fun manager(vararg files: Pair<String, String>): ResourceManager {
        val resources = files.associate { (id, json) -> ResourceLocation.parse(id) to resource(json) }
        return Proxy.newProxyInstance(ResourceManager::class.java.classLoader, arrayOf(ResourceManager::class.java)) { _, method, args ->
            when (method.name) {
                "listResources" -> {
                    @Suppress("UNCHECKED_CAST")
                    val predicate = args[1] as Predicate<ResourceLocation>
                    resources.filterKeys { it.path.startsWith("${args[0]}/") && predicate.test(it) }
                }
                "toString" -> "test resource manager"
                else -> error("Unexpected resource manager call: ${method.name}")
            }
        } as ResourceManager
    }

    @Test fun `pack removal clears its rules and custom categories`() {
        loader.onResourceManagerReload(manager("testpack:battle_music/music.json" to """{
            "id":"music","rules":[{"type":"category","match":"boss","sound":["test:theme"]}],
            "categories":{"boss":["cobblemon:eevee"]}
        }"""))
        assertEquals("pack:testpack:battle_music/music.json", loader.snapshot.rules.single().source)
        val battle = BattleContext(UUID(0, 1), BattleKind.WILD, opponents = listOf(OpponentPokemon("cobblemon:eevee")))
        assertNotNull(RuleMatcher().select(battle, loader.snapshot).winner)

        loader.onResourceManagerReload(manager())
        assertEquals(RuleSnapshot(), loader.snapshot)
        assertNull(RuleMatcher().select(battle, loader.snapshot).winner)
    }

    @Test fun `pack updates replace previous rules and category membership`() {
        fun music(species: String, sound: String) = """{"id":"music",
            "rules":[{"type":"category","match":"boss","sound":["$sound"]}],
            "categories":{"boss":["$species"]}}"""
        loader.onResourceManagerReload(manager("testpack:battle_music/music.json" to music("cobblemon:eevee", "test:old")))
        loader.onResourceManagerReload(manager("testpack:battle_music/music.json" to music("cobblemon:mew", "test:new")))
        assertEquals(listOf("test:new"), loader.snapshot.rules.single().sound)
        assertEquals(setOf(SpeciesSelector("cobblemon:mew")), loader.snapshot.categories["boss"])
    }

    @Test fun `independent pack rules compete by priority and malformed files do not discard valid siblings`() {
        loader.onResourceManagerReload(manager(
            "first:battle_music/music.json" to """{"id":"first","rules":[{"type":"wild","sound":["test:low"]}]}""",
            "second:battle_music/music.json" to """{"id":"second","priority":100,"rules":[{"type":"all","sound":["test:high"]}]}""",
            "second:battle_music/broken.json" to "{broken",
            "second:battle_music/notes.txt" to "not JSON"
        ))
        assertEquals(2, loader.snapshot.rules.size)
        assertTrue(loader.snapshot.rules.all { it.source.startsWith("pack:") })
        val battle = BattleContext(UUID(0, 1), BattleKind.WILD)
        assertEquals(listOf("test:high"), RuleMatcher().select(battle, loader.snapshot).winner!!.rule.sound)
    }
}
