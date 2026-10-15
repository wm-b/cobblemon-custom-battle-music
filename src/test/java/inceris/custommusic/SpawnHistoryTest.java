package inceris.custommusic;

import inceris.custommusic.audio.*;
import inceris.custommusic.battle.*;
import inceris.custommusic.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static inceris.custommusic.TestFixtures.copy;
import static inceris.custommusic.TestFixtures.only;
import inceris.custommusic.spawn.PersistentSpawnHistory;

class SpawnHistoryTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private final UUID id = new UUID(0, 1);

    @Test
    @DisplayName("wild verification excludes both player and NPC stored Pokemon")
    void testWildVerificationExcludesBothPlayerAndNPCStoredPokemon() throws Exception {
        assertTrue(inceris.custommusic.spawn.WildAppearanceHandler.isWild(null, null));
            assertFalse(inceris.custommusic.spawn.WildAppearanceHandler.isWild(id, null));
            assertFalse(inceris.custommusic.spawn.WildAppearanceHandler.isWild(null, id));
            assertFalse(inceris.custommusic.spawn.WildAppearanceHandler.isWild(id, id));
    }

    @Test
    @DisplayName("UUID record prevents repeats after reload reconnect and game restart")
    void testUUIDRecordPreventsRepeatsAfterReloadReconnectAndGameRestart() throws Exception {
        try (var history = new PersistentSpawnHistory(directory)) {
                assertTrue(history.claim("server:a", id));
                for (int i = 0; i < 100; i++) assertFalse(history.claim("server:a", id));
                history.close();
                assertFalse(history.claim("server:a", id));
            }
            try (var history = new PersistentSpawnHistory(directory)) {
                assertFalse(history.claim("server:a", id)); assertTrue(history.claim("server:a", new UUID(0, 2)));
            }
    }

    @Test
    @DisplayName("servers and worlds have independent histories")
    void testServersAndWorldsHaveIndependentHistories() throws Exception {
        try (var history = new PersistentSpawnHistory(directory)) {
                assertTrue(history.claim("server:a", id)); assertTrue(history.claim("world:b", id));
                assertFalse(history.claim("server:a", id)); assertFalse(history.claim("world:b", id));
            }
    }

    @Test
    @DisplayName("corrupt history fails closed and never permits memory-only playback")
    void testCorruptHistoryFailsClosedAndNeverPermitsMemoryOnlyPlayback() throws Exception {
        try (var history = new PersistentSpawnHistory(directory)) {
                Files.writeString(history.pathFor("server:a"), "corrupted\n");
                assertThrows(IOException.class, () -> history.claim("server:a", id));
                assertFalse(history.claim("server:a", id)); assertTrue(history.claim("server:b", id));
            }
    }

    @Test
    @DisplayName("unwritable history never grants a playback claim")
    void testUnwritableHistoryNeverGrantsAPlaybackClaim() throws Exception {
        var file = directory.resolve("file"); Files.writeString(file, "not a directory");
            try (var history = new PersistentSpawnHistory(file)) {
                assertThrows(IOException.class, () -> history.claim("server:a", id));
                assertFalse(history.claim("server:a", id));
            }
    }

    @Test
    @DisplayName("partially written records fail closed")
    void testPartiallyWrittenRecordsFailClosed() throws Exception {
        for (String record : List.of(id.toString(), "00000000-0000-0000-0000-0000")) {
                var historyDirectory = Files.createTempDirectory(directory, "partial");
                try (var history = new PersistentSpawnHistory(historyDirectory)) {
                    Files.writeString(history.pathFor("server:a"), record);
                    assertThrows(IOException.class, () -> history.claim("server:a", new UUID(0, 2)));
                    assertFalse(history.claim("server:a", new UUID(0, 2)));
                }
            }
    }

    @Test
    @DisplayName("concurrent clients cannot both claim the same Pokemon")
    void testConcurrentClientsCannotBothClaimTheSamePokemon() throws Exception {
        try (var first = new PersistentSpawnHistory(directory); var second = new PersistentSpawnHistory(directory)) {
                assertTrue(first.claim("server:a", id));
                assertThrows(IOException.class, () -> second.claim("server:a", id));
                assertFalse(second.claim("server:a", id));
            }
            try (var history = new PersistentSpawnHistory(directory)) { assertFalse(history.claim("server:a", id)); }
    }

    @Test
    @DisplayName("moving history out of config preserves records across upgrade and restart")
    void testMovingHistoryOutOfConfigPreservesRecordsAcrossUpgradeAndRestart() throws Exception {
        var oldHistory = directory.resolve("config/mod/appearance_history");
            var newHistory = directory.resolve(".mod/appearance_history");
            try (var history = new PersistentSpawnHistory(oldHistory)) { assertTrue(history.claim("server:a", id)); }
            var oldRules = oldHistory.getParent().resolve("battle_music/music.json");
            Files.createDirectories(oldRules.getParent()); Files.writeString(oldRules, "legacy user rules");
            try (var history = new PersistentSpawnHistory(newHistory, oldHistory)) {
                assertFalse(history.claim("server:a", id)); assertTrue(history.claim("server:a", new UUID(0, 2)));
            }
            assertFalse(Files.exists(oldHistory)); assertEquals("legacy user rules", Files.readString(oldRules));
            try (var history = new PersistentSpawnHistory(newHistory, oldHistory)) {
                assertFalse(history.claim("server:a", id)); assertFalse(history.claim("server:a", new UUID(0, 2)));
            }
    }

    @Test
    @DisplayName("conflicting old and new histories fail closed without discarding records")
    void testConflictingOldAndNewHistoriesFailClosedWithoutDiscardingRecords() throws Exception {
        var oldHistory = directory.resolve("old"); var newHistory = directory.resolve("new");
            try (var history = new PersistentSpawnHistory(oldHistory)) { assertTrue(history.claim("server:a", id)); }
            try (var history = new PersistentSpawnHistory(newHistory)) { assertTrue(history.claim("server:a", new UUID(0, 2))); }
            try (var history = new PersistentSpawnHistory(newHistory, oldHistory)) {
                assertThrows(IOException.class, () -> history.claim("server:a", new UUID(0, 3)));
                assertFalse(history.claim("server:a", new UUID(0, 3)));
            }
            try (var history = new PersistentSpawnHistory(oldHistory)) { assertFalse(history.claim("server:a", id)); }
            try (var history = new PersistentSpawnHistory(newHistory)) { assertFalse(history.claim("server:a", new UUID(0, 2))); }
    }
}
