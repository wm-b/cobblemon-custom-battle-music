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

    @Test fun `pinned intro hooks retain looping field constructors and restart getter`() {
        val instance = node("com/cobblemon/mod/common/client/sound/instances/BattleMusicInstance")
        assertEquals("net/minecraft/client/resources/sounds/SimpleSoundInstance", instance.superName)
        assertTrue(instance.methods.any { it.name == "<init>" && it.desc == "(Lnet/minecraft/sounds/SoundEvent;FF)V" })
        assertTrue(node("net/minecraft/client/resources/sounds/AbstractSoundInstance").fields.any { it.name == "looping" && it.desc == "Z" })
        val handler = node("com/cobblemon/mod/common/client/net/battle/BattleMusicHandler").methods.single {
            it.name == "handle" && it.desc.contains("BattleMusicPacket;")
        }
        assertEquals(1, handler.instructions.toArray().filterIsInstance<MethodInsnNode>().count {
            it.owner == "com/cobblemon/mod/common/net/messages/client/battle/BattleMusicPacket" && it.name == "getRestartExisting"
        })
    }

    @Test fun `pinned spawn metadata is applied before client entity registration`() {
        val spawn = node("com/cobblemon/mod/common/net/messages/client/spawn/SpawnExtraDataEntityPacket")
            .methods.single { it.name == "spawnAndApply" }
        val calls = spawn.instructions.toArray().filterIsInstance<MethodInsnNode>()
        val dataIndex = calls.indexOfFirst { it.name == "applyData" }
        val loadIndex = calls.indexOfFirst { it.owner == "net/minecraft/client/multiplayer/ClientLevel" && it.name == "addEntity" }
        assertTrue(dataIndex >= 0 && loadIndex > dataIndex)
        val packet = node("com/cobblemon/mod/common/net/messages/client/spawn/SpawnPokemonPacket")
        assertTrue(packet.methods.any { it.name == "getOwnerId" && it.desc == "()Ljava/util/UUID;" })
        assertTrue(packet.methods.any { it.name == "getStorageUUID" && it.desc == "()Ljava/util/UUID;" })
        assertTrue(packet.methods.any { it.name == "getPokemonUUID" && it.desc == "()Ljava/util/UUID;" })
        assertTrue(packet.methods.any { it.name == "applyData" && it.desc == "(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/client/multiplayer/ClientLevel;)V" })
    }

    @Test fun `pinned HP bar uses the documented red boundary`() {
        val helper = node("com/cobblemon/mod/common/client/render/RenderHelperKt").methods.single {
            it.name == "getDepletableRedGreen\$default"
        }
        assertTrue(helper.instructions.toArray().filterIsInstance<org.objectweb.asm.tree.LdcInsnNode>().any { it.cst == 0.2f })
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
        assertTrue(rules.map { it.type }.containsAll(listOf(RuleType.TRAINER, RuleType.SPECIES, RuleType.CATEGORY, RuleType.WILD, RuleType.ALL, RuleType.PLAYER)))
        assertTrue(rules.any { it.sound.size > 1 }, "README must demonstrate multiple tracks")
        rules.forEach { rule ->
            (rule.sound + rule.intro + rule.lowAllyHp + rule.lowEnemyHp).forEach { sound ->
                assertTrue(sound.startsWith("cobblemon:"))
                assertTrue(sounds.has(sound.substringAfter(':')), "Missing documented sound $sound")
            }
        }
    }
}
