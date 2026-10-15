package inceris.custommusic.audio;

import inceris.custommusic.rules.MusicRule;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Pure battle-local playback state. HP changes never reroll tracks or replay an intro. */
public final class MusicProgram {
    private final RandomTrackSelector mainTracks, introTracks, lowAllyTracks, lowEnemyTracks;
    private record Signature(String key, List<String> sound, List<String> intro, List<String> lowAllyHp, List<String> lowEnemyHp) {}
    private final Set<Signature> consumedIntros = new HashSet<>();
    private Signature signature;
    private String main, intro, lowAlly, lowEnemy;
    private MusicStage stage = MusicStage.MAIN;
    private boolean introObserved;
    private int introWaitTicks;
    private ProgramTrack desired;

    public MusicProgram() { this(bound -> ThreadLocalRandom.current().nextInt(bound)); }
    public MusicProgram(IntUnaryOperator chooseIndex) {
        mainTracks = new RandomTrackSelector(chooseIndex);
        introTracks = new RandomTrackSelector(chooseIndex);
        lowAllyTracks = new RandomTrackSelector(chooseIndex);
        lowEnemyTracks = new RandomTrackSelector(chooseIndex);
    }
    public ProgramTrack desired() { return desired; }

    public ProgramTrack select(MusicRule rule, boolean lowAllyHealth, boolean lowEnemyHealth, Predicate<String> available) {
        Signature next = rule == null ? null : new Signature(rule.key(), rule.sound(), rule.intro(), rule.lowAllyHp(), rule.lowEnemyHp());
        if (!Objects.equals(next, signature)) {
            signature = next;
            main = mainTracks.select(rule);
            intro = introTracks.select(rule, rule == null ? List.of() : rule.intro());
            lowAlly = lowAllyTracks.select(rule, rule == null ? List.of() : rule.lowAllyHp());
            lowEnemy = lowEnemyTracks.select(rule, rule == null ? List.of() : rule.lowEnemyHp());
            stage = next != null && !consumedIntros.contains(next) && intro != null ? MusicStage.INTRO : MusicStage.MAIN;
            introObserved = false;
            introWaitTicks = 0;
        }
        if (main == null || !available.test(main)) { finishIntro(); desired = null; return null; }
        if (stage == MusicStage.INTRO && (intro == null || !available.test(intro))) finishIntro();
        MusicStage lowStage = lowAllyHealth && lowAlly != null && available.test(lowAlly) ? MusicStage.LOW_ALLY_HP
                : lowEnemyHealth && lowEnemy != null && available.test(lowEnemy) ? MusicStage.LOW_ENEMY_HP : null;
        if (lowStage != null) { finishIntro(); stage = lowStage; }
        else if (stage == MusicStage.LOW_ALLY_HP || stage == MusicStage.LOW_ENEMY_HP) stage = MusicStage.MAIN;
        desired = new ProgramTrack(switch (stage) {
            case INTRO -> intro;
            case MAIN -> main;
            case LOW_ALLY_HP -> lowAlly;
            case LOW_ENEMY_HP -> lowEnemy;
        }, stage);
        return desired;
    }

    /** Call only when the native controller owns the selected intro instance. */
    public void observeIntro(boolean active) {
        if (stage != MusicStage.INTRO) return;
        if (active) introObserved = true;
        else if (introObserved || ++introWaitTicks >= 100) finishIntro();
    }

    public void finishIntro() {
        if (stage != MusicStage.INTRO) return;
        if (signature != null) consumedIntros.add(signature);
        stage = MusicStage.MAIN;
        introObserved = false;
        introWaitTicks = 0;
        desired = main == null ? null : new ProgramTrack(main, MusicStage.MAIN);
    }

    public void clear() {
        mainTracks.clear(); introTracks.clear(); lowAllyTracks.clear(); lowEnemyTracks.clear();
        consumedIntros.clear(); signature = null;
        main = intro = lowAlly = lowEnemy = null;
        stage = MusicStage.MAIN; desired = null; introObserved = false; introWaitTicks = 0;
    }
}
