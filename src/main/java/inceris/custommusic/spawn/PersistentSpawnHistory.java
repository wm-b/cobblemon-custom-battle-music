package inceris.custommusic.spawn;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

/** Append-only ledger. A forced disk write must succeed before the caller can request playback. */
public final class PersistentSpawnHistory implements AutoCloseable {
    private final Path directory, legacyDirectory;
    private String scope;
    private FileChannel channel;
    private FileLock lock;
    private final Set<UUID> seen = new HashSet<>();
    private final Set<String> failedScopes = new HashSet<>();

    public PersistentSpawnHistory(Path directory) { this(directory, null); }
    public PersistentSpawnHistory(Path directory, Path legacyDirectory) {
        this.directory = directory; this.legacyDirectory = legacyDirectory;
    }
    public synchronized boolean claim(String identity, UUID pokemon) throws IOException {
        if (failedScopes.contains(identity)) return false;
        try {
            if (!Objects.equals(scope, identity)) open(identity);
            if (seen.contains(pokemon)) return false;
            var output = Objects.requireNonNull(channel);
            output.position(output.size());
            var bytes = UTF_8.encode(pokemon + "\n");
            while (bytes.hasRemaining()) output.write(bytes);
            output.force(true);
            seen.add(pokemon);
            return true;
        } catch (Exception exception) {
            // Never fall back to memory-only playback: that loses once-only state on reconnect.
            failedScopes.add(identity);
            try { close(); } catch (IOException closeError) { exception.addSuppressed(closeError); }
            throw new IOException("Wild appearance history is unavailable; sounds disabled for this world/server", exception);
        }
    }
    private void open(String identity) throws IOException {
        close();
        // Move only generated history, preserving consumed UUIDs across the config path change.
        if (legacyDirectory != null && Files.exists(legacyDirectory)) {
            if (Files.exists(directory)) throw new IOException("Both old and new appearance histories exist; refusing to discard records");
            Files.createDirectories(directory.getParent());
            Files.move(legacyDirectory, directory);
        }
        Files.createDirectories(directory);
        channel = FileChannel.open(pathFor(identity), CREATE, READ, WRITE);
        lock = channel.tryLock();
        if (lock == null) throw new IOException("Appearance history is in use by another client");
        long size = channel.size();
        if (size > 0) {
            var lastByte = ByteBuffer.allocate(1);
            channel.read(lastByte, size - 1);
            if (lastByte.array()[0] != '\n') throw new IOException("Truncated appearance history record");
        }
        // Read through the locked channel, including on Windows. Corrupt records fail closed.
        var reader = new BufferedReader(Channels.newReader(channel, UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            var uuid = UUID.fromString(line);
            if (!uuid.toString().equals(line)) throw new IOException("Invalid appearance history record");
            seen.add(uuid);
        }
        scope = identity;
    }
    public Path pathFor(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".seen");
        } catch (NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }
    @Override public synchronized void close() throws IOException {
        try { if (lock != null) lock.release(); }
        finally {
            lock = null;
            try { if (channel != null) channel.close(); }
            finally { channel = null; scope = null; seen.clear(); }
        }
    }
}
