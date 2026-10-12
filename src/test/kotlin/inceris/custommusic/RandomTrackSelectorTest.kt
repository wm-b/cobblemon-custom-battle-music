package inceris.custommusic

import inceris.custommusic.audio.RandomTrackSelector
import inceris.custommusic.rules.MusicRule
import inceris.custommusic.rules.RuleType
import org.junit.jupiter.api.Test
import kotlin.test.*

class RandomTrackSelectorTest {
    private val rule = MusicRule("test.json", "music", 0, RuleType.TRAINER, "gym",
        listOf("test:first", "test:second", "test:third"))

    @Test fun `every configured track is eligible and selection uses the full array`() {
        for (index in rule.sound.indices) {
            val selector = RandomTrackSelector { bound ->
                assertEquals(rule.sound.size, bound)
                index
            }
            assertEquals(rule.sound[index], selector.select(rule))
        }
    }

    @Test fun `ticks repeat packets and reloads with unchanged tracks do not reroll`() {
        var draws = 0
        val selector = RandomTrackSelector { draws++; 1 }
        assertEquals("test:second", selector.select(rule))
        repeat(100) { assertEquals("test:second", selector.select(rule.copy())) }
        assertEquals("test:second", selector.select(rule.copy(priority = 200)))
        assertEquals(1, draws)
    }

    @Test fun `a new winning rule draws another track even with the same array`() {
        var draws = 0
        val selector = RandomTrackSelector { draws++ % it }
        assertEquals("test:first", selector.select(rule))
        assertEquals("test:second", selector.select(rule.copy(index = 1)))
        assertEquals(2, draws)
    }

    @Test fun `editing the winning track array draws from its new contents`() {
        var draws = 0
        val selector = RandomTrackSelector { draws++; it - 1 }
        assertEquals("test:third", selector.select(rule))
        assertEquals("test:new", selector.select(rule.copy(sound = listOf("test:replacement", "test:new"))))
        assertEquals(2, draws)
    }

    @Test fun `ending a battle clears the choice so the next battle draws again`() {
        var draws = 0
        val selector = RandomTrackSelector { draws++ % it }
        assertEquals("test:first", selector.select(rule))
        selector.clear()
        assertEquals("test:second", selector.select(rule))
        assertEquals(2, draws)
    }

    @Test fun `losing the match resets selection without consuming randomness for fallback`() {
        var draws = 0
        val selector = RandomTrackSelector { draws++ % it }
        assertNull(selector.select(null))
        assertEquals("test:first", selector.select(rule))
        assertNull(selector.select(null))
        assertNull(selector.select(null))
        assertEquals("test:second", selector.select(rule))
        assertEquals(2, draws)
    }

    @Test fun `a one track array always selects that track`() {
        val selector = RandomTrackSelector()
        val single = rule.copy(sound = listOf("test:only"))
        repeat(10) {
            selector.clear()
            assertEquals("test:only", selector.select(single))
        }
    }
}
