package inceris.custommusic.audio

import inceris.custommusic.rules.MusicRule

enum class MusicStage { INTRO, MAIN, LOW_ALLY_HP, LOW_ENEMY_HP }
data class ProgramTrack(val sound: String, val stage: MusicStage) {
    val looping: Boolean get() = stage != MusicStage.INTRO
}

/** Pure battle-local playback state. HP changes never reroll tracks or replay an intro. */
class MusicProgram(chooseIndex: (Int) -> Int = { kotlin.random.Random.nextInt(it) }) {
    private val mainTracks = RandomTrackSelector(chooseIndex)
    private val introTracks = RandomTrackSelector(chooseIndex)
    private val lowAllyTracks = RandomTrackSelector(chooseIndex)
    private val lowEnemyTracks = RandomTrackSelector(chooseIndex)
    private data class Signature(val key: String, val sound: List<String>, val intro: List<String>,
        val lowAllyHp: List<String>, val lowEnemyHp: List<String>)
    private val consumedIntros = mutableSetOf<Signature>()
    private var signature: Signature? = null
    private var main: String? = null
    private var intro: String? = null
    private var lowAlly: String? = null
    private var lowEnemy: String? = null
    private var stage = MusicStage.MAIN
    private var introObserved = false
    private var introWaitTicks = 0
    var desired: ProgramTrack? = null
        private set

    fun select(rule: MusicRule?, lowAllyHealth: Boolean, lowEnemyHealth: Boolean, available: (String) -> Boolean): ProgramTrack? {
        val next = rule?.let { Signature(it.key, it.sound, it.intro, it.lowAllyHp, it.lowEnemyHp) }
        if (next != signature) {
            signature = next
            main = mainTracks.select(rule)
            intro = introTracks.select(rule, rule?.intro.orEmpty())
            lowAlly = lowAllyTracks.select(rule, rule?.lowAllyHp.orEmpty())
            lowEnemy = lowEnemyTracks.select(rule, rule?.lowEnemyHp.orEmpty())
            stage = if (next != null && next !in consumedIntros && intro != null) MusicStage.INTRO else MusicStage.MAIN
            introObserved = false
            introWaitTicks = 0
        }
        if (main == null || !available(main!!)) {
            finishIntro()
            desired = null
            return null
        }
        if (stage == MusicStage.INTRO && (intro == null || !available(intro!!))) finishIntro()
        val lowStage = when {
            lowAllyHealth && lowAlly?.let(available) == true -> MusicStage.LOW_ALLY_HP
            lowEnemyHealth && lowEnemy?.let(available) == true -> MusicStage.LOW_ENEMY_HP
            else -> null
        }
        if (lowStage != null) {
            finishIntro()
            stage = lowStage
        } else if (stage == MusicStage.LOW_ALLY_HP || stage == MusicStage.LOW_ENEMY_HP) stage = MusicStage.MAIN
        desired = when (stage) {
            MusicStage.INTRO -> ProgramTrack(intro!!, stage)
            MusicStage.MAIN -> ProgramTrack(main!!, stage)
            MusicStage.LOW_ALLY_HP -> ProgramTrack(lowAlly!!, stage)
            MusicStage.LOW_ENEMY_HP -> ProgramTrack(lowEnemy!!, stage)
        }
        return desired
    }

    /** Call only when the native controller owns the selected intro instance. */
    fun observeIntro(active: Boolean) {
        if (stage != MusicStage.INTRO) return
        if (active) introObserved = true
        else if (introObserved || ++introWaitTicks >= 100) finishIntro()
    }

    fun finishIntro() {
        if (stage != MusicStage.INTRO) return
        signature?.let(consumedIntros::add)
        stage = MusicStage.MAIN
        introObserved = false
        introWaitTicks = 0
        desired = main?.let { ProgramTrack(it, MusicStage.MAIN) }
    }

    fun clear() {
        mainTracks.clear()
        introTracks.clear()
        lowAllyTracks.clear()
        lowEnemyTracks.clear()
        consumedIntros.clear()
        signature = null
        main = null
        intro = null
        lowAlly = null
        lowEnemy = null
        stage = MusicStage.MAIN
        desired = null
        introObserved = false
        introWaitTicks = 0
    }
}
