package inceris.custommusic.audio

import inceris.custommusic.rules.MusicRule
import kotlin.random.Random

/** Pick once when a winning rule becomes active; repeated ticks/packets keep that track. */
class RandomTrackSelector(private val chooseIndex: (Int) -> Int = { Random.nextInt(it) }) {
    private var ruleKey: String? = null
    private var sounds: List<String> = emptyList()
    private var track: String? = null

    fun select(rule: MusicRule?): String? {
        if (rule == null) {
            clear()
            return null
        }
        if (ruleKey != rule.key || sounds != rule.sound) {
            ruleKey = rule.key
            sounds = rule.sound.toList()
            track = sounds[chooseIndex(sounds.size)]
        }
        return track
    }

    fun clear() {
        ruleKey = null
        sounds = emptyList()
        track = null
    }
}
