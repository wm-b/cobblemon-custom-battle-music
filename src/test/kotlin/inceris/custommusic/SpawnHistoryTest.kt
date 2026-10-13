package inceris.custommusic

import inceris.custommusic.spawn.PersistentSpawnHistory
import inceris.custommusic.spawn.WildAppearanceHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class SpawnHistoryTest {
    @TempDir lateinit var directory: Path
    private val id = UUID(0, 1)

    @Test fun `wild verification excludes both player and NPC stored Pokemon`() {
        assertTrue(WildAppearanceHandler.isWild(null, null))
        assertFalse(WildAppearanceHandler.isWild(id, null))
        assertFalse(WildAppearanceHandler.isWild(null, id))
        assertFalse(WildAppearanceHandler.isWild(id, id))
    }

    @Test fun `UUID record prevents repeats after reload reconnect and game restart`() {
        PersistentSpawnHistory(directory).use { history ->
            assertTrue(history.claim("server:a", id))
            repeat(100) { assertFalse(history.claim("server:a", id)) }
            history.close()
            assertFalse(history.claim("server:a", id))
        }
        PersistentSpawnHistory(directory).use { assertFalse(it.claim("server:a", id)); assertTrue(it.claim("server:a", UUID(0, 2))) }
    }

    @Test fun `servers and worlds have independent histories`() {
        PersistentSpawnHistory(directory).use {
            assertTrue(it.claim("server:a", id))
            assertTrue(it.claim("world:b", id))
            assertFalse(it.claim("server:a", id))
            assertFalse(it.claim("world:b", id))
        }
    }

    @Test fun `corrupt history fails closed and never permits memory-only playback`() {
        PersistentSpawnHistory(directory).use {
            Files.writeString(it.pathFor("server:a"), "corrupted\n")
            assertFailsWith<IOException> { it.claim("server:a", id) }
            assertFalse(it.claim("server:a", id))
            assertTrue(it.claim("server:b", id))
        }
    }

    @Test fun `unwritable history never grants a playback claim`() {
        val file = directory.resolve("file")
        Files.writeString(file, "not a directory")
        PersistentSpawnHistory(file).use {
            assertFailsWith<IOException> { it.claim("server:a", id) }
            assertFalse(it.claim("server:a", id))
        }
    }

    @Test fun `partially written records fail closed`() {
        for (record in listOf("$id", "00000000-0000-0000-0000-0000")) {
            val historyDirectory = Files.createTempDirectory(directory, "partial")
            PersistentSpawnHistory(historyDirectory).use {
                Files.writeString(it.pathFor("server:a"), record)
                assertFailsWith<IOException> { it.claim("server:a", UUID(0, 2)) }
                assertFalse(it.claim("server:a", UUID(0, 2)))
            }
        }
    }

    @Test fun `concurrent clients cannot both claim the same Pokemon`() {
        PersistentSpawnHistory(directory).use { first ->
            PersistentSpawnHistory(directory).use { second ->
                assertTrue(first.claim("server:a", id))
                assertFailsWith<IOException> { second.claim("server:a", id) }
                assertFalse(second.claim("server:a", id))
            }
        }
        PersistentSpawnHistory(directory).use { assertFalse(it.claim("server:a", id)) }
    }

    @Test fun `moving history out of config preserves records across upgrade and restart`() {
        val oldHistory = directory.resolve("config/mod/appearance_history")
        val newHistory = directory.resolve(".mod/appearance_history")
        PersistentSpawnHistory(oldHistory).use { assertTrue(it.claim("server:a", id)) }
        val oldRules = oldHistory.parent.resolve("battle_music/music.json")
        Files.createDirectories(oldRules.parent)
        Files.writeString(oldRules, "legacy user rules")

        PersistentSpawnHistory(newHistory, oldHistory).use {
            assertFalse(it.claim("server:a", id))
            assertTrue(it.claim("server:a", UUID(0, 2)))
        }
        assertFalse(Files.exists(oldHistory))
        assertEquals("legacy user rules", Files.readString(oldRules))
        PersistentSpawnHistory(newHistory, oldHistory).use {
            assertFalse(it.claim("server:a", id))
            assertFalse(it.claim("server:a", UUID(0, 2)))
        }
    }

    @Test fun `conflicting old and new histories fail closed without discarding records`() {
        val oldHistory = directory.resolve("old")
        val newHistory = directory.resolve("new")
        PersistentSpawnHistory(oldHistory).use { assertTrue(it.claim("server:a", id)) }
        PersistentSpawnHistory(newHistory).use { assertTrue(it.claim("server:a", UUID(0, 2))) }
        PersistentSpawnHistory(newHistory, oldHistory).use {
            assertFailsWith<IOException> { it.claim("server:a", UUID(0, 3)) }
            assertFalse(it.claim("server:a", UUID(0, 3)))
        }
        PersistentSpawnHistory(oldHistory).use { assertFalse(it.claim("server:a", id)) }
        PersistentSpawnHistory(newHistory).use { assertFalse(it.claim("server:a", UUID(0, 2))) }
    }
}
