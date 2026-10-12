package inceris.custommusic

import com.google.gson.JsonParser
import inceris.custommusic.rules.RuleParser
import inceris.custommusic.rules.RuleType
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Inspects the dependency without initializing Minecraft or pretending to test live gameplay. */
class IntegrationContractTest {
    private fun node(name: String): ClassNode {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$name.class"))
        return stream.use { ClassNode().also { node -> ClassReader(it).accept(node, 0) } }
    }

    @Test fun `pinned music handler contains one sound factory invocation and native playback hooks`() {
        val name = "com/cobblemon/mod/common/client/net/battle/BattleMusicHandler"
        val handler = node(name).methods.single { it.name == "handle" && it.desc.contains("BattleMusicPacket;") }
        val calls = handler.instructions.toArray().filterIsInstance<MethodInsnNode>()
        assertEquals(1, calls.count { it.owner == "net/minecraft/sounds/SoundEvent" && it.name == "createVariableRangeEvent" })
        val native = calls.filter { it.owner == "com/cobblemon/mod/common/client/sound/BattleMusicController" }.map { it.name }
        assertTrue(native.containsAll(listOf("initializeMusic", "switchMusic", "endMusic")))
    }

    @Test fun `pinned client cleanup retains native music ending`() {
        val handler = node("com/cobblemon/mod/common/client/CobblemonClient").methods.single { it.name == "endBattle" }
        assertTrue(handler.instructions.toArray().filterIsInstance<MethodInsnNode>().any {
            it.owner == "com/cobblemon/mod/common/client/sound/BattleMusicController" && it.name == "endMusic"
        })
    }

    @Test fun `mixin config explicitly loads the generated Minecraft reference map`() {
        val config = Files.newBufferedReader(Path.of("src/main/resources/custommusic.client.mixins.json")).use { JsonParser.parseReader(it).asJsonObject }
        val refmapName = config["refmap"].asString
        val refmapPath = Path.of("build/classes/java/main", refmapName)
        assertTrue(Files.exists(refmapPath), "Missing generated mixin refmap")
        val refmap = Files.newBufferedReader(refmapPath).use { JsonParser.parseReader(it).asJsonObject }
        val mapping = refmap["mappings"].asJsonObject["inceris/custommusic/mixin/BattleMusicHandlerMixin"].asJsonObject
        assertTrue(mapping.keySet().any { it.contains("createVariableRangeEvent") })
        assertTrue(mapping.entrySet().any { it.value.asString.contains("class_3414;method_47908") })
    }

    @Test fun `README rules parse without diagnostics and refer to sounds shipped by Cobblemon`() {
        val sounds = requireNotNull(javaClass.getResourceAsStream("/assets/cobblemon/sounds.json")).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val readme = Files.readString(Path.of("README.md"))
        val snippets = Regex("```json\\s*\\n([\\s\\S]*?)\\n```").findAll(readme).toList()
        assertTrue(snippets.isNotEmpty(), "README must include a usable rule document")
        val documents = snippets.mapIndexed { index, snippet ->
            RuleParser().parse(snippet.groupValues[1].reader(), "README.md#$index").also {
                assertTrue(it.diagnostics.isEmpty(), "${it.diagnostics}")
                assertTrue(it.rules.isNotEmpty(), "README rule document must contain rules")
            }
        }
        val rules = documents.flatMap { it.rules }
        assertTrue(rules.map { it.type }.containsAll(listOf(RuleType.TRAINER, RuleType.SPECIES, RuleType.CATEGORY)))
        assertTrue(rules.any { it.sound.size > 1 }, "README must demonstrate multiple tracks")
        rules.forEach { rule ->
            rule.sound.forEach { sound ->
                assertTrue(sound.startsWith("cobblemon:"))
                assertTrue(sounds.has(sound.substringAfter(':')), "Missing documented sound $sound")
            }
        }
    }
}
