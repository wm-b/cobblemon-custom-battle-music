package inceris.custommusic

import inceris.custommusic.audio.*
import inceris.custommusic.rules.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class MusicProgramTest {
    private val rule = MusicRule("test", "music", 0, RuleType.ALL, "", listOf("test:main", "test:other"),
        intro = listOf("test:intro"), lowAllyHp = listOf("test:low", "test:low_other"),
        lowEnemyHp = listOf("test:enemy", "test:enemy_other"))
    private fun MusicProgram.select(rule: MusicRule? = this@MusicProgramTest.rule, ally: Boolean = false, enemy: Boolean = false) =
        select(rule, ally, enemy) { true }

    @Test fun `intro plays once before main and survives unchanged rules`() {
        val program = MusicProgram { 0 }
        assertEquals(ProgramTrack("test:intro", MusicStage.INTRO), program.select())
        assertFalse(program.desired!!.looping)
        repeat(10) { program.observeIntro(true); assertEquals(MusicStage.INTRO, program.select(rule.copy(priority = 100))!!.stage) }
        program.observeIntro(false)
        assertEquals(ProgramTrack("test:main", MusicStage.MAIN), program.select())
        assertTrue(program.desired!!.looping)
        program.select(null)
        assertEquals(MusicStage.MAIN, program.select()!!.stage)
        program.clear()
        assertEquals(MusicStage.INTRO, program.select()!!.stage)
    }

    @Test fun `either low HP stage interrupts intro and tracks stay stable through side changes`() {
        var draws = 0
        val program = MusicProgram { draws++; it - 1 }
        program.select()
        assertEquals(ProgramTrack("test:enemy_other", MusicStage.LOW_ENEMY_HP), program.select(enemy = true))
        assertEquals(ProgramTrack("test:low_other", MusicStage.LOW_ALLY_HP), program.select(ally = true, enemy = true))
        assertEquals("test:other", program.select()!!.sound)
        repeat(100) {
            assertEquals("test:low_other", program.select(ally = true)!!.sound)
            assertEquals("test:enemy_other", program.select(enemy = true)!!.sound)
            assertEquals("test:other", program.select()!!.sound)
        }
        assertEquals(4, draws)
    }

    @Test fun `missing optional tracks fall back and missing main uses native theme`() {
        val program = MusicProgram { 0 }
        assertEquals(MusicStage.MAIN, program.select(rule, true, true) { it == "test:main" }!!.stage)
        assertNull(program.select(rule, false, false) { false })
        assertEquals(MusicStage.MAIN, program.select()!!.stage)
    }

    @Test fun `intro waits for audio startup and proceeds if playback never starts`() {
        val program = MusicProgram { 0 }
        program.select()
        repeat(99) { program.observeIntro(false); assertEquals(MusicStage.INTRO, program.select()!!.stage) }
        program.observeIntro(false)
        assertEquals(MusicStage.MAIN, program.select()!!.stage)
    }

    @Test fun `changing tracks or winning rule starts its own intro`() {
        val program = MusicProgram { 0 }
        program.select()
        program.observeIntro(true)
        program.observeIntro(false)
        assertEquals(MusicStage.INTRO, program.select(rule.copy(intro = listOf("test:new_intro")))!!.stage)
        assertEquals(MusicStage.INTRO, program.select(rule.copy(index = 1))!!.stage)
    }

    @Test fun `low HP at battle start skips intro and default rules loop normally`() {
        val program = MusicProgram { 0 }
        assertEquals(MusicStage.LOW_ALLY_HP, program.select(ally = true)!!.stage)
        assertEquals(MusicStage.MAIN, program.select()!!.stage)
        assertEquals(MusicStage.MAIN, program.select(rule.copy(intro = emptyList(), lowAllyHp = emptyList(), lowEnemyHp = emptyList()))!!.stage)
    }

    @Test fun `ally stage has priority while missing or unconfigured ally tracks allow enemy stage`() {
        val program = MusicProgram { 0 }
        assertEquals(ProgramTrack("test:low", MusicStage.LOW_ALLY_HP), program.select(ally = true, enemy = true))
        assertEquals(ProgramTrack("test:enemy", MusicStage.LOW_ENEMY_HP),
            program.select(rule, true, true) { it != "test:low" })
        assertEquals(ProgramTrack("test:enemy", MusicStage.LOW_ENEMY_HP),
            program.select(rule.copy(lowAllyHp = emptyList()), ally = true, enemy = true))
        assertEquals(MusicStage.MAIN, program.select(rule.copy(intro = emptyList(), lowEnemyHp = emptyList()), enemy = true)!!.stage)
    }

    @Test fun `missing enemy track preserves intro when ally stage does not trigger`() {
        val program = MusicProgram { 0 }
        assertEquals(MusicStage.INTRO, program.select(rule, false, true) { it != "test:enemy" }!!.stage)
        assertEquals(MusicStage.LOW_ALLY_HP, program.select(rule, true, true) { it != "test:enemy" }!!.stage)
    }

    @Test fun `enemy low HP at battle start skips intro and clear resets both stages`() {
        val program = MusicProgram { 0 }
        assertEquals(MusicStage.LOW_ENEMY_HP, program.select(enemy = true)!!.stage)
        assertEquals(MusicStage.MAIN, program.select()!!.stage)
        program.clear()
        assertEquals(MusicStage.INTRO, program.select()!!.stage)
    }

    @Test fun `editing one low HP track list preserves other selections`() {
        var draws = 0
        val program = MusicProgram { draws++; it - 1 }
        program.select(ally = true, enemy = true)
        val changed = rule.copy(lowEnemyHp = listOf("test:new_enemy"))
        assertEquals(ProgramTrack("test:low_other", MusicStage.LOW_ALLY_HP), program.select(changed, ally = true))
        assertEquals(ProgramTrack("test:new_enemy", MusicStage.LOW_ENEMY_HP), program.select(changed, enemy = true))
        assertEquals(5, draws)
    }
}
