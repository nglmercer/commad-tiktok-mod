package dev.nglmercer.tiktokminecraft.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Portable hot reload watcher. Scans an intentionally small subset of the plugin
 * directory on a daemon thread. Content fingerprints (rather than WatchService)
 * support atomic editor saves and filesystems that coalesce/miss rename events.
 * Bukkit interaction is delegated to the caller and MUST run on the main thread.
 */
public final class LiveFileWatcher implements AutoCloseable {
    private static final int MAX_FILES = 1024;
    private static final int MAX_DEPTH = 8;
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private final Path root;
    private final Consumer<List<String>> onSettledChange;
    private final Consumer<String> error;
    private final ScheduledExecutorService worker;

    private volatile long debounceMillis;
    private volatile boolean enabled;
    private volatile boolean closed;
    private String baseline;
    private String pending;
    private long changedAtNanos;
    private boolean initialized;

    public LiveFileWatcher(Path root, boolean enabled, long debounceMillis,
            Consumer<List<String>> onSettledChange, Consumer<String> error) {
        this.root = root.toAbsolutePath().normalize();
        this.enabled = enabled;
        this.debounceMillis = Math.max(250L, Math.min(5000L, debounceMillis));
        this.onSettledChange = onSettledChange;
        this.error = error;
        this.worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tiktokminecraft-hot-reload");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts polling every 500 ms; content changes settle for the configured debounce. */
    public void start() {
        worker.scheduleWithFixedDelay(this::pollSafely, 0L, 500L, TimeUnit.MILLISECONDS);
    }

    public void configure(boolean enabled, long debounceMillis) {
        this.enabled = enabled;
        this.debounceMillis = Math.max(250L, Math.min(5000L, debounceMillis));
    }

    public boolean enabled() { return enabled; }

    private void pollSafely() {
        if (closed) return;
        try {
            String state = fingerprint();
            if (!initialized) {
                baseline = state;
                initialized = true;
                return;
            }
            if (!state.equals(baseline)) {
                if (!state.equals(pending)) {
                    pending = state;
                    changedAtNanos = System.nanoTime();
                } else if ((System.nanoTime() - changedAtNanos) / 1_000_000L >= debounceMillis) {
                    baseline = state;
                    pending = null;
                    if (enabled) onSettledChange.accept(List.of("config.yml", "events/", "functions/", "scripts/"));
                }
            } else {
                pending = null;
            }
        } catch (Exception failure) {
            error.accept("File watcher scan failed: " + failure.getMessage());
        }
    }

    /**
     * A sorted SHA-256 of paths and contents. Temporary editor files, backups,
     * symlinks, and generated cache/exports are deliberately ignored.
     */
    private String fingerprint() throws IOException {
        MessageDigest digest = digest();
        List<Path> paths = new ArrayList<>();
        Path config = root.resolve("config.yml");
        if (Files.isRegularFile(config) && !Files.isSymbolicLink(config)) paths.add(config);
        for (String folder : List.of("events", "functions", "scripts")) {
            Path directory = root.resolve(folder);
            if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) continue;
            try (var files = Files.walk(directory, MAX_DEPTH)) {
                files.filter(Files::isRegularFile)
                     .filter(path -> !Files.isSymbolicLink(path))
                     .filter(this::supported)
                     .forEach(paths::add);
            }
        }
        if (paths.size() > MAX_FILES) throw new IOException("Too many watched files");
        paths.sort(Comparator.comparing(path -> root.relativize(path).toString()));
        for (Path path : paths) {
            update(digest, root.relativize(path).toString());
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE) throw new IOException("File too large: " + path.getFileName());
            byte[] bytes = Files.readAllBytes(path);
            update(digest, Integer.toString(bytes.length));
            digest.update(bytes);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith(".") || name.endsWith("~") || name.contains(".bak")
                || name.endsWith(".tmp") || name.endsWith(".swp")) return false;
        Path relative = root.relativize(path);
        String folder = relative.getName(0).toString();
        if ("events".equals(folder)) return name.endsWith(".yml");
        if ("functions".equals(folder)) return name.endsWith(".json") || name.endsWith(".mcfunction");
        return "scripts".equals(folder) && name.endsWith(".ttl");
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
    }
}
