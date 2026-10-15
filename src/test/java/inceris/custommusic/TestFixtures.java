package inceris.custommusic;

import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import java.util.*;
import java.util.function.Consumer;

/** Explicit copies replace Kotlin's named arguments without adding production builders. */
final class TestFixtures {
    private TestFixtures() {}
    static <T> T only(List<T> values) {
        org.junit.jupiter.api.Assertions.assertEquals(1, values.size(), "Expected exactly one item");
        return values.getFirst();
    }
    static MusicRule copy(MusicRule rule, Consumer<RuleBuilder> change) {
        var builder = new RuleBuilder(rule);
        change.accept(builder);
        return builder.build();
    }
    static BattleContext copy(BattleContext context, Consumer<ContextBuilder> change) {
        var builder = new ContextBuilder(context);
        change.accept(builder);
        return builder.build();
    }
    static final class RuleBuilder {
        String source, documentId, match, form;
        int index, priority;
        RuleType type;
        RuleScope scope;
        RuleEvent event;
        List<String> sound, intro, lowAllyHp, lowEnemyHp;
        RuleBuilder(MusicRule rule) {
            source = rule.source(); documentId = rule.documentId(); index = rule.index(); type = rule.type();
            match = rule.match(); form = rule.form(); priority = rule.priority(); scope = rule.scope(); event = rule.event();
            sound = rule.sound(); intro = rule.intro(); lowAllyHp = rule.lowAllyHp(); lowEnemyHp = rule.lowEnemyHp();
        }
        MusicRule build() { return new MusicRule(source, documentId, index, type, match, sound, priority, form, scope, event, intro, lowAllyHp, lowEnemyHp); }
    }
    static final class ContextBuilder {
        UUID battleId;
        BattleKind kind;
        List<TrainerIdentity> trainers;
        List<OpponentPokemon> opponents;
        List<PlayerIdentity> players;
        boolean spectating, lowAllyHealth, lowEnemyHealth;
        ContextBuilder(BattleContext context) {
            battleId = context.battleId(); kind = context.kind(); trainers = context.trainers(); opponents = context.opponents();
            players = context.players(); spectating = context.spectating(); lowAllyHealth = context.lowAllyHealth(); lowEnemyHealth = context.lowEnemyHealth();
        }
        BattleContext build() { return new BattleContext(battleId, kind, trainers, opponents, spectating, players, lowAllyHealth, lowEnemyHealth); }
    }
}
