package inceris.custommusic.battle;

import com.cobblemon.mod.common.client.sound.BattleMusicController;
import inceris.custommusic.audio.*;
import inceris.custommusic.rules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import java.util.*;

/** Client-thread state. Trainer identity persists when its entity unloads. */
public final class BattleLifecycleHandler {
    private final RuleLoader loader;
    private final Logger logger;
    private final BattleContextResolver resolver = new BattleContextResolver();
    private final RuleMatcher matcher = new RuleMatcher();
    private final MusicProgram program = new MusicProgram();
    private BattleContext context, lastContext;
    private ResourceLocation defaultSound, selectedSound;
    private boolean suspended;
    private RuleSnapshot lastSnapshot;
    private MusicRule matchedRule;
    private record LogEntry(String rule, ResourceLocation sound) {}
    private LogEntry lastLog;
    private final Set<String> warnings = new HashSet<>();

    public BattleLifecycleHandler(RuleLoader loader, Logger logger) { this.loader = loader; this.logger = logger; }
    public ResourceLocation resolveMusic(ResourceLocation original) {
        var client = Minecraft.getInstance();
        refreshContext(client);
        defaultSound = original;
        suspended = false;
        // Validate again on every server music request in case resources changed.
        lastSnapshot = null;
        var chosen = choose(client);
        return chosen == null ? original : chosen;
    }
    public void onMusicHandled() {
        if (BattleMusicController.INSTANCE.getMusic().isFading()) suspended = true;
        observeIntro(Minecraft.getInstance());
    }
    public boolean isIntro(ResourceLocation sound) {
        var desired = program.desired();
        return !suspended && desired != null && desired.stage() == MusicStage.INTRO && desired.sound().equals(sound.toString());
    }
    private void observeIntro(Minecraft client) {
        var current = BattleMusicController.INSTANCE.getMusic();
        if (isIntro(current.getLocation())) program.observeIntro(client.getSoundManager().isActive(current));
    }
    public void tick(Minecraft client) {
        refreshContext(client);
        if (context == null || suspended || defaultSound == null) return;
        observeIntro(client);
        var sound = choose(client);
        if (sound == null) sound = defaultSound;
        if (sound != null) CobblemonMusicAdapter.switchTo(sound, client, !isIntro(sound));
    }
    private void refreshContext(Minecraft client) {
        var resolved = resolver.resolve(client);
        if (resolved == null) {
            if (context != null) { CobblemonMusicAdapter.end(); clear(); }
            return;
        }
        var previous = context;
        if (previous == null || !previous.battleId().equals(resolved.battleId())) {
            clear(); context = resolved;
            logger.debug("Battle context: {}", resolved);
        } else {
            // Retain late synchronized identity if its entity leaves tracking range.
            List<TrainerIdentity> trainers = new ArrayList<>();
            for (int i = 0; i < resolved.trainers().size(); i++) {
                var trainer = resolved.trainers().get(i);
                trainers.add(trainer.id() != null || i >= previous.trainers().size() ? trainer : previous.trainers().get(i));
            }
            List<PlayerIdentity> players = resolved.players().stream().map(player -> player.username() != null ? player
                    : previous.players().stream().filter(old -> old.uuid().equals(player.uuid())).findFirst().orElse(player)).toList();
            context = new BattleContext(resolved.battleId(), resolved.kind(), trainers, resolved.opponents(), resolved.spectating(), players,
                    resolved.lowAllyHealth(), resolved.lowEnemyHealth());
        }
    }
    private ResourceLocation choose(Minecraft client) {
        var battle = context;
        if (battle == null) return null;
        var snapshot = loader.snapshot();
        if (lastSnapshot != snapshot || !Objects.equals(lastContext, battle)) {
            lastSnapshot = snapshot; lastContext = battle;
            var selection = matcher.select(battle, snapshot);
            if (selection.tied().size() > 1) {
                var keys = selection.tied().stream().map(match -> match.rule().key()).toList();
                warnOnce("tie:" + keys, "Equal-priority battle music tie; using " + selection.winner().rule().key() + ". Candidates: " + keys);
            }
            matchedRule = selection.winner() == null ? null : selection.winner().rule();
        }
        var track = program.select(matchedRule, battle.lowAllyHealth(), battle.lowEnemyHealth(), name -> {
            var sound = ResourceLocation.tryParse(name);
            boolean available = sound != null && CobblemonMusicAdapter.isAvailable(sound, client);
            if (!available) warnOnce("missing:" + name, "Missing or empty sound " + name + " (" + (matchedRule == null ? null : matchedRule.key()) + "); skipping this playback stage");
            return available;
        });
        selectedSound = track == null ? null : ResourceLocation.tryParse(track.sound());
        var log = new LogEntry(matchedRule == null ? null : matchedRule.key(), selectedSound);
        if (!Objects.equals(lastLog, log)) logger.debug("Battle {} theme: {} (rule {}, stage {})", battle.battleId(),
                selectedSound == null ? defaultSound : selectedSound, matchedRule == null ? "default" : matchedRule.key(), track == null ? "default" : track.stage());
        lastLog = log;
        return selectedSound;
    }
    private void warnOnce(String key, String message) { if (warnings.add(key)) logger.warn(message); }
    public void clear() {
        program.clear(); context = lastContext = null; defaultSound = selectedSound = null; matchedRule = null;
        lastLog = null; lastSnapshot = null; suspended = false; warnings.clear();
    }
    public void disconnect() { if (context != null) CobblemonMusicAdapter.end(); clear(); }
}
