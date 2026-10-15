package inceris.custommusic;

import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import inceris.custommusic.battle.BattleLifecycleHandler;
import inceris.custommusic.rules.RuleLoader;
import inceris.custommusic.spawn.WildAppearanceHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.slf4j.LoggerFactory;

public final class CustomBattleMusicClient implements ClientModInitializer {
    public static final String MOD_ID = CustomBattleMusic.MOD_ID;
    public static final String ASSET_NAMESPACE = CustomBattleMusic.ASSET_NAMESPACE;
    private static BattleLifecycleHandler lifecycle;
    private static WildAppearanceHandler appearances;

    @Override public void onInitializeClient() {
        var logger = LoggerFactory.getLogger(MOD_ID);
        var loader = new RuleLoader(logger);
        var handler = new BattleLifecycleHandler(loader, logger);
        lifecycle = handler;
        var gameDirectory = FabricLoader.getInstance().getGameDir();
        var spawnHandler = new WildAppearanceHandler(loader, gameDirectory.resolve("." + MOD_ID).resolve("appearance_history"), logger,
                // Preserve once-only state from earlier versions. No configuration files are read.
                gameDirectory.resolve("config").resolve(MOD_ID).resolve("appearance_history"));
        appearances = spawnHandler;
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(loader);
        ClientTickEvents.END_CLIENT_TICK.register(client -> { handler.tick(client); spawnHandler.tick(client); });
        ClientPlayConnectionEvents.INIT.register((connection, client) -> { handler.disconnect(); spawnHandler.disconnect(); });
        ClientPlayConnectionEvents.JOIN.register((connection, sender, client) -> handler.disconnect());
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> { handler.disconnect(); spawnHandler.disconnect(); });
        logger.info("Initialized client battle music resolver for Cobblemon 1.8.2");
    }
    public static ResourceLocation resolveMusic(ResourceLocation original) { return lifecycle == null ? original : lifecycle.resolveMusic(original); }
    public static void onMusicHandled() { if (lifecycle != null) lifecycle.onMusicHandled(); }
    public static boolean isIntro(ResourceLocation sound) { return lifecycle != null && lifecycle.isIntro(sound); }
    public static void onPokemonSpawn(SpawnPokemonPacket packet) { if (appearances != null) appearances.onSpawn(packet); }
    public static void onBattleEnded() { if (lifecycle != null) lifecycle.clear(); }
}
