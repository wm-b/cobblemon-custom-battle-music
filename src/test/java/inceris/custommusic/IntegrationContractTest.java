package inceris.custommusic;

import static inceris.custommusic.TestFixtures.only;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

class IntegrationContractTest {
  private ClassNode node(String name) throws IOException {
    try (var stream =
        Objects.requireNonNull(getClass().getResourceAsStream("/" + name + ".class"))) {
      var node = new ClassNode();
      new ClassReader(stream).accept(node, 0);
      return node;
    }
  }

  private MethodNode method(String owner, String name) throws IOException {
    return only(node(owner).methods.stream().filter(method -> method.name.equals(name)).toList());
  }

  private List<MethodInsnNode> calls(MethodNode method) {
    return Arrays.stream(method.instructions.toArray())
        .filter(MethodInsnNode.class::isInstance)
        .map(MethodInsnNode.class::cast)
        .toList();
  }

  private JsonObject json(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  @Test
  @DisplayName("packaged dependency accepts the Cobblemon 1.8 series and rejects other series")
  void testPackagedDependencyAcceptsTheCobblemon18SeriesAndRejectsOtherSeries() throws Exception {
    var metadata = json(Path.of("build/resources/main/fabric.mod.json"));
    var predicate =
        VersionPredicate.parse(metadata.getAsJsonObject("depends").get("cobblemon").getAsString());
    for (String version : List.of("1.8.0", "1.8.1", "1.8.2", "1.8.3", "1.8.3+1.21.1", "1.8.99")) {
      assertTrue(predicate.test(Version.parse(version)), "Must accept Cobblemon " + version);
    }
    for (String version : List.of("1.7.3", "1.9.0", "2.0.0")) {
      assertFalse(predicate.test(Version.parse(version)), "Must reject Cobblemon " + version);
    }
  }

  @Test
  @DisplayName(
      "pinned music handler contains one sound factory invocation and native playback hooks")
  void testPinnedMusicHandlerContainsOneSoundFactoryInvocationAndNativePlaybackHooks()
      throws Exception {
    var handler =
        only(
            node("com/cobblemon/mod/common/client/net/battle/BattleMusicHandler").methods.stream()
                .filter(
                    method ->
                        method.name.equals("handle") && method.desc.contains("BattleMusicPacket;"))
                .toList());
    var calls = calls(handler);
    assertEquals(
        1,
        calls.stream()
            .filter(
                call ->
                    call.owner.equals("net/minecraft/sounds/SoundEvent")
                        && call.name.equals("createVariableRangeEvent"))
            .count());
    var nativeCalls =
        calls.stream()
            .filter(
                call ->
                    call.owner.equals(
                        "com/cobblemon/mod/common/client/sound/BattleMusicController"))
            .map(call -> call.name)
            .toList();
    assertTrue(nativeCalls.containsAll(List.of("initializeMusic", "switchMusic", "endMusic")));
  }

  @Test
  @DisplayName("pinned client cleanup retains native music ending")
  void testPinnedClientCleanupRetainsNativeMusicEnding() throws Exception {
    var handler = method("com/cobblemon/mod/common/client/CobblemonClient", "endBattle");
    assertTrue(
        calls(handler).stream()
            .anyMatch(
                call ->
                    call.owner.equals("com/cobblemon/mod/common/client/sound/BattleMusicController")
                        && call.name.equals("endMusic")));
  }

  @Test
  @DisplayName("pinned intro hooks retain looping field constructors and restart getter")
  void testPinnedIntroHooksRetainLoopingFieldConstructorsAndRestartGetter() throws Exception {
    var instance = node("com/cobblemon/mod/common/client/sound/instances/BattleMusicInstance");
    assertEquals("net/minecraft/client/resources/sounds/SimpleSoundInstance", instance.superName);
    assertTrue(
        instance.methods.stream()
            .anyMatch(
                method ->
                    method.name.equals("<init>")
                        && method.desc.equals("(Lnet/minecraft/sounds/SoundEvent;FF)V")));
    assertTrue(
        node("net/minecraft/client/resources/sounds/AbstractSoundInstance").fields.stream()
            .anyMatch(field -> field.name.equals("looping") && field.desc.equals("Z")));
    var handler =
        only(
            node("com/cobblemon/mod/common/client/net/battle/BattleMusicHandler").methods.stream()
                .filter(
                    method ->
                        method.name.equals("handle") && method.desc.contains("BattleMusicPacket;"))
                .toList());
    assertEquals(
        1,
        calls(handler).stream()
            .filter(
                call ->
                    call.owner.equals(
                            "com/cobblemon/mod/common/net/messages/client/battle/BattleMusicPacket")
                        && call.name.equals("getRestartExisting"))
            .count());
  }

  @Test
  @DisplayName("pinned spawn metadata is applied before client entity registration")
  void testPinnedSpawnMetadataIsAppliedBeforeClientEntityRegistration() throws Exception {
    var calls =
        calls(
            method(
                "com/cobblemon/mod/common/net/messages/client/spawn/SpawnExtraDataEntityPacket",
                "spawnAndApply"));
    int dataIndex = -1, loadIndex = -1;
    for (int i = 0; i < calls.size(); i++) {
      var call = calls.get(i);
      if (dataIndex < 0 && call.name.equals("applyData")) dataIndex = i;
      if (loadIndex < 0
          && call.owner.equals("net/minecraft/client/multiplayer/ClientLevel")
          && call.name.equals("addEntity")) loadIndex = i;
    }
    assertTrue(dataIndex >= 0 && loadIndex > dataIndex);
    var packet = node("com/cobblemon/mod/common/net/messages/client/spawn/SpawnPokemonPacket");
    for (String getter : List.of("getOwnerId", "getStorageUUID", "getPokemonUUID")) {
      assertTrue(
          packet.methods.stream()
              .anyMatch(
                  method ->
                      method.name.equals(getter) && method.desc.equals("()Ljava/util/UUID;")));
    }
    assertTrue(
        packet.methods.stream()
            .anyMatch(
                method ->
                    method.name.equals("applyData")
                        && method.desc.equals(
                            "(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/client/multiplayer/ClientLevel;)V")));
  }

  @Test
  @DisplayName("pinned HP bar uses the documented red boundary")
  void testPinnedHPBarUsesTheDocumentedRedBoundary() throws Exception {
    var helper =
        method(
            "com/cobblemon/mod/common/client/render/RenderHelperKt",
            "getDepletableRedGreen$default");
    assertTrue(
        Arrays.stream(helper.instructions.toArray())
            .filter(LdcInsnNode.class::isInstance)
            .map(LdcInsnNode.class::cast)
            .anyMatch(instruction -> Float.valueOf(0.2f).equals(instruction.cst)));
  }

  @Test
  @DisplayName("mixin config explicitly loads the generated Minecraft reference map")
  void testMixinConfigExplicitlyLoadsTheGeneratedMinecraftReferenceMap() throws Exception {
    var config = json(Path.of("src/main/resources/custommusic.client.mixins.json"));
    String refmapName = config.get("refmap").getAsString();
    var refmapPath = Path.of("build/classes/java/main", refmapName);
    assertTrue(Files.exists(refmapPath), "Missing generated mixin refmap");
    var mapping =
        json(refmapPath)
            .getAsJsonObject("mappings")
            .getAsJsonObject("inceris/custommusic/mixin/BattleMusicHandlerMixin");
    assertTrue(mapping.keySet().stream().anyMatch(key -> key.contains("createVariableRangeEvent")));
    assertTrue(
        mapping.entrySet().stream()
            .anyMatch(entry -> entry.getValue().getAsString().contains("class_3414;method_47908")));
  }

  @Test
  @DisplayName("README rules parse without diagnostics and refer to sounds shipped by Cobblemon")
  void testREADMERulesParseWithoutDiagnosticsAndReferToSoundsShippedByCobblemon() throws Exception {
    JsonObject sounds;
    try (var reader =
        new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/assets/cobblemon/sounds.json")),
            java.nio.charset.StandardCharsets.UTF_8)) {
      sounds = JsonParser.parseReader(reader).getAsJsonObject();
    }
    String readme = Files.readString(Path.of("README.md"));
    var snippets =
        java.util.regex.Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```").matcher(readme);
    List<MusicRule> rules = new ArrayList<>();
    int index = 0;
    while (snippets.find()) {
      var document =
          new RuleParser().parse(new StringReader(snippets.group(1)), "README.md#" + index++);
      assertTrue(document.diagnostics().isEmpty(), document.diagnostics().toString());
      assertFalse(document.rules().isEmpty(), "README rule document must contain rules");
      rules.addAll(document.rules());
    }
    assertTrue(index > 0, "README must include a usable rule document");
    assertTrue(
        rules.stream()
            .map(MusicRule::type)
            .toList()
            .containsAll(
                List.of(
                    RuleType.TRAINER,
                    RuleType.SPECIES,
                    RuleType.CATEGORY,
                    RuleType.WILD,
                    RuleType.ALL,
                    RuleType.PLAYER)));
    assertTrue(
        rules.stream().anyMatch(rule -> rule.sound().size() > 1),
        "README must demonstrate multiple tracks");
    for (var rule : rules)
      for (var stage : List.of(rule.sound(), rule.intro(), rule.lowAllyHp(), rule.lowEnemyHp()))
        for (String sound : stage) {
          assertTrue(sound.startsWith("cobblemon:"));
          assertTrue(
              sounds.has(sound.substring(sound.indexOf(':') + 1)),
              "Missing documented sound " + sound);
        }
  }
}
