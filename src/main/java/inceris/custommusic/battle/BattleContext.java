package inceris.custommusic.battle;

import java.util.List;
import java.util.UUID;

public record BattleContext(UUID battleId, BattleKind kind, List<TrainerIdentity> trainers,
        List<OpponentPokemon> opponents, boolean spectating, List<PlayerIdentity> players,
        boolean lowAllyHealth, boolean lowEnemyHealth) {
    public BattleContext {
        trainers = List.copyOf(trainers);
        opponents = List.copyOf(opponents);
        players = List.copyOf(players);
    }
    public BattleContext(UUID battleId, BattleKind kind) {
        this(battleId, kind, List.of(), List.of(), false, List.of(), false, false);
    }
}
