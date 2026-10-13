package inceris.custommusic

import inceris.custommusic.battle.BattleLifecycleHandler
import inceris.custommusic.rules.RuleLoader
import inceris.custommusic.spawn.WildAppearanceHandler
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import org.slf4j.LoggerFactory

object CustomBattleMusicClient : ClientModInitializer {
    const val MOD_ID = CustomBattleMusic.MOD_ID
    const val ASSET_NAMESPACE = CustomBattleMusic.ASSET_NAMESPACE
    private val logger = LoggerFactory.getLogger(MOD_ID)
    private var lifecycle: BattleLifecycleHandler? = null
    private var appearances: WildAppearanceHandler? = null

    override fun onInitializeClient() {
        val loader = RuleLoader(logger)
        val handler = BattleLifecycleHandler(loader, logger)
        lifecycle = handler
        val gameDirectory = FabricLoader.getInstance().gameDir
        val spawnHandler = WildAppearanceHandler(loader, gameDirectory.resolve(".$MOD_ID").resolve("appearance_history"), logger,
            // Preserve once-only state from earlier versions. No configuration files are read.
            legacyHistoryDirectory = gameDirectory.resolve("config").resolve(MOD_ID).resolve("appearance_history"))
        appearances = spawnHandler
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(loader)
        ClientTickEvents.END_CLIENT_TICK.register { client -> handler.tick(client); spawnHandler.tick(client) }
        ClientPlayConnectionEvents.INIT.register { _, _ -> handler.disconnect(); spawnHandler.disconnect() }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> handler.disconnect() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> handler.disconnect(); spawnHandler.disconnect() }
        logger.info("Initialized client battle music resolver for Cobblemon 1.8.2")
    }

    @JvmStatic fun resolveMusic(original: ResourceLocation): ResourceLocation = lifecycle?.resolveMusic(original) ?: original
    @JvmStatic fun onMusicHandled() { lifecycle?.onMusicHandled() }
    @JvmStatic fun isIntro(sound: ResourceLocation): Boolean = lifecycle?.isIntro(sound) == true
    @JvmStatic fun onPokemonSpawn(packet: SpawnPokemonPacket) { appearances?.onSpawn(packet) }
    @JvmStatic fun onBattleEnded() { lifecycle?.clear() }
}
