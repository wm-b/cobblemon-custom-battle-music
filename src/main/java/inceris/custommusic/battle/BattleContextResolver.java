package inceris.custommusic.battle;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import inceris.custommusic.rules.SpeciesCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static inceris.custommusic.rules.RuleStrings.normalize;

public final class BattleContextResolver {
    private final TrainerIdentityResolver identities;
    public BattleContextResolver() { this(new TrainerIdentityResolver()); }
    public BattleContextResolver(TrainerIdentityResolver identities) { this.identities = identities; }

    public BattleContext resolve(Minecraft client) {
        var battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || battle.getSpectating() || client.player == null) return null;
        // side1 is the participant's side; side2 contains actual opponents.
        var actors = battle.getSide2().getActors();
        BattleKind kind = actors.stream().anyMatch(actor -> actor.getType() == ActorType.NPC) ? BattleKind.TRAINER
                : !actors.isEmpty() && actors.stream().allMatch(actor -> actor.getType() == ActorType.WILD) ? BattleKind.WILD : BattleKind.PLAYER;
        List<TrainerIdentity> trainers = new ArrayList<>();
        List<OpponentPokemon> opponents = new ArrayList<>();
        List<PlayerIdentity> players = new ArrayList<>();
        for (var actor : actors) {
            if (actor.getType() == ActorType.NPC) trainers.add(identities.resolve(actor, client));
            if (actor.getType() == ActorType.PLAYER) {
                var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(actor.getUuid());
                players.add(new PlayerIdentity(actor.getUuid(), info == null ? null : info.getProfile().getName()));
            }
            for (var active : actor.getActivePokemon()) {
                var pokemon = active.getBattlePokemon();
                if (pokemon == null || pokemon.getProperties().getSpecies() == null) continue;
                String raw = pokemon.getProperties().getSpecies();
                var speciesId = ResourceLocation.tryParse(raw.contains(":") ? normalize(raw) : "cobblemon:" + normalize(raw));
                if (speciesId == null) continue;
                var species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
                var nativeForm = species == null ? null : species.getForm(pokemon.getState().getCurrentAspects());
                String form = pokemon.getProperties().getForm();
                if (form == null && nativeForm != null) form = nativeForm.formOnlyShowdownId();
                form = form == null ? "normal" : normalize(form);
                Set<String> labels = new LinkedHashSet<>(SpeciesCategories.labels(speciesId.toString(), form));
                if (nativeForm != null) labels.addAll(nativeForm.getLabels());
                opponents.add(new OpponentPokemon(speciesId.toString(), form, PokemonTraits.categories(labels,
                        Boolean.TRUE.equals(pokemon.getProperties().getShiny()) || pokemon.getState().getCurrentAspects().contains("shiny"))));
            }
        }
        return new BattleContext(battle.getBattleId(), kind, trainers, opponents, false, players,
                hasLowHealth(battle.getSide1()), hasLowHealth(battle.getSide2()));
    }

    private static boolean hasLowHealth(ClientBattleSide side) {
        for (var actor : side.getActors()) for (var active : actor.getActivePokemon()) {
            var pokemon = active.getBattlePokemon();
            if (pokemon != null && PokemonTraits.isLowHealth(pokemon.getHpValue(), pokemon.getMaxHp(), pokemon.isHpFlat())) return true;
        }
        return false;
    }
}
