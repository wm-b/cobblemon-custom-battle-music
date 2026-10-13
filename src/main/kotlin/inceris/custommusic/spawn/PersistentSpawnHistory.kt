package inceris.custommusic.spawn

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.UUID

/** Append-only ledger. A forced disk write must succeed before the caller can request playback. */
class PersistentSpawnHistory(private val directory: Path, private val legacyDirectory: Path? = null) : AutoCloseable {
    private var scope: String? = null
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private val seen = mutableSetOf<UUID>()
    private val failedScopes = mutableSetOf<String>()

    @Synchronized
    fun claim(identity: String, pokemon: UUID): Boolean {
        if (identity in failedScopes) return false
        try {
            if (scope != identity) open(identity)
            if (pokemon in seen) return false
            val output = requireNotNull(channel)
            output.position(output.size())
            val bytes = UTF_8.encode("$pokemon\n")
            while (bytes.hasRemaining()) output.write(bytes)
            output.force(true)
            seen.add(pokemon)
            return true
        } catch (exception: Exception) {
            // Never fall back to memory-only playback: that would lose the once-only promise on reconnect.
            failedScopes.add(identity)
            close()
            throw IOException("Wild appearance history is unavailable; sounds disabled for this world/server", exception)
        }
    }

    private fun open(identity: String) {
        close()
        // Move only generated history, preserving previously consumed UUIDs across this path change.
        if (legacyDirectory != null && Files.exists(legacyDirectory)) {
            require(!Files.exists(directory)) { "Both old and new appearance histories exist; refusing to discard records" }
            Files.createDirectories(directory.parent)
            Files.move(legacyDirectory, directory)
        }
        Files.createDirectories(directory)
        channel = FileChannel.open(pathFor(identity), CREATE, READ, WRITE)
        lock = channel!!.tryLock() ?: throw IOException("Appearance history is in use by another client")
        val size = channel!!.size()
        if (size > 0) {
            val lastByte = ByteBuffer.allocate(1)
            channel!!.read(lastByte, size - 1)
            require(lastByte.array()[0] == '\n'.code.toByte()) { "Truncated appearance history record" }
        }
        // Read through the locked channel, including on Windows. A corrupt record fails closed.
        val reader = Channels.newReader(channel!!, UTF_8).buffered()
        reader.lineSequence().forEach { line ->
            val uuid = UUID.fromString(line)
            require(uuid.toString() == line) { "Invalid appearance history record" }
            seen.add(uuid)
        }
        scope = identity
    }

    fun pathFor(identity: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(UTF_8))
        return directory.resolve(digest.joinToString("") { "%02x".format(it) } + ".seen")
    }

    @Synchronized
    override fun close() {
        try { lock?.release() } finally {
            lock = null
            try { channel?.close() } finally {
                channel = null
                scope = null
                seen.clear()
            }
        }
    }
}
