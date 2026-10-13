package inceris.custommusic.audio

import inceris.custommusic.rules.MusicRule
import kotlin.random.Random

/** Pick once when a winning rule becomes active; repeated ticks/packets keep that track. */
class RandomTrackSelector(private val chooseIndex: (Int) -> Int = { Random.nextInt(it) }) {
    private var ruleKey: String? = null
    private var sounds: List<String> = emptyList()
    private var track: String? = null

    fun select(rule: MusicRule?, candidates: List<String> = rule?.sound.orEmpty()): String? {
        if (rule == null || candidates.isEmpty()) {
            clear()
            return null
        }
        if (ruleKey != rule.key || sounds != candidates) {
            ruleKey = rule.key
            sounds = candidates.toList()
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
