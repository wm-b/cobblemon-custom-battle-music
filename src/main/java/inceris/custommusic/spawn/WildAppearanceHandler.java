package inceris.custommusic.spawn;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import inceris.custommusic.audio.CobblemonMusicAdapter;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static inceris.custommusic.rules.RuleStrings.normalize;

/** Client tracking notifications, verified from fully decoded Cobblemon ownership metadata. */
public final class WildAppearanceHandler {
    private final RuleLoader loader;
    private final Logger logger;
    private record Candidate(UUID pokemon, int entityId, ClientLevel level) {}
    private final Map<UUID, Candidate> pending = new LinkedHashMap<>();
    private final RuleMatcher matcher = new RuleMatcher();
    private final PersistentSpawnHistory history;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "custommusic-appearance-history");
        thread.setDaemon(true);
        return thread;
    });
    private long generation;
    private final Set<String> warnings = new HashSet<>();

    public WildAppearanceHandler(RuleLoader loader, Path historyDirectory, Logger logger, Path legacyHistoryDirectory) {
        this.loader = loader; this.logger = logger;
        history = new PersistentSpawnHistory(historyDirectory, legacyHistoryDirectory);
    }
    public void onSpawn(SpawnPokemonPacket packet) {
        if (!isWild(packet.getOwnerId(), packet.getStorageUUID())) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        pending.put(packet.getPokemonUUID(), new Candidate(packet.getPokemonUUID(), packet.getVanillaSpawnPacket().getId(), level));
    }
    public void tick(Minecraft client) {
        if (pending.isEmpty() || client.player == null) return;
        String identity = scope(client);
        if (identity == null) return;
        var batch = List.copyOf(pending.values());
        pending.clear();
        for (var candidate : batch) {
            if (candidate.level() != client.level) continue;
            if (!(candidate.level().getEntity(candidate.entityId()) instanceof PokemonEntity entity)) continue;
            if (!entity.getPokemon().getUuid().equals(candidate.pokemon()) || entity.getOwnerUUID() != null) continue;
            var pokemon = entity.getPokemon();
            String species = pokemon.getSpecies().getResourceIdentifier().toString();
            String form = pokemon.getForm().formOnlyShowdownId();
            Set<String> labels = new LinkedHashSet<>(pokemon.getForm().getLabels());
            labels.addAll(SpeciesCategories.labels(species, form));
            var categories = PokemonTraits.categories(labels, pokemon.getShiny());
            var context = new BattleContext(candidate.pokemon(), BattleKind.WILD, List.of(),
                    List.of(new OpponentPokemon(species, form, categories)), false, List.of(), false, false);
            var winner = matcher.select(context, loader.snapshot(), RuleEvent.SPAWN).winner();
            if (winner == null) continue;
            var rule = winner.rule();
            var sound = ResourceLocation.tryParse(rule.sound().get(ThreadLocalRandom.current().nextInt(rule.sound().size())));
            if (sound == null) continue;
            if (!CobblemonMusicAdapter.isAvailable(sound, client)) {
                if (warnings.add("missing:" + sound)) logger.warn("Missing appearance sound {} ({}); skipping", sound, rule.key());
                continue;
            }
            long token = generation;
            CompletableFuture.supplyAsync(() -> {
                try { return history.claim(identity, candidate.pokemon()); }
                catch (IOException exception) { throw new CompletionException(exception); }
            }, writer).whenComplete((claimed, error) -> {
                if (error != null) logger.warn("Unable to record appearance sound: {}",
                        error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                else if (Boolean.TRUE.equals(claimed)) client.execute(() -> {
                    if (token != generation || client.level != candidate.level() || entity.isRemoved() || entity.getOwnerUUID() != null) return;
                    // NEUTRAL stays audible during battle music; never enters the battle controller.
                    client.getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(sound), SoundSource.NEUTRAL,
                            1f, 1f, RandomSource.create(), entity.getX(), entity.getY(), entity.getZ()));
                });
            });
        }
    }
    public void disconnect() {
        generation++;
        pending.clear(); warnings.clear();
        writer.execute(() -> {
            try { history.close(); }
            catch (Exception exception) { logger.warn("Unable to close appearance history: {}", exception.getMessage()); }
        });
    }
    private String scope(Minecraft client) {
        var server = client.getSingleplayerServer();
        Path world = server == null ? null : server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var current = client.getCurrentServer();
        String address = current == null ? null : normalize(current.ip);
        String location = world != null ? "world:" + world : address != null ? "server:" + address : null;
        return location == null ? null : location + "|listener:" + client.getUser().getProfileId();
    }
    public static boolean isWild(UUID owner, UUID storage) { return owner == null && storage == null; }
}
