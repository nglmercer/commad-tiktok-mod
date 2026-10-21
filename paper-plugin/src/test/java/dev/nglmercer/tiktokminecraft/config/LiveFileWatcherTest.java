package dev.nglmercer.tiktokminecraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression coverage for editor saves, nested functions, and watcher cleanup. */
class LiveFileWatcherTest {
    @TempDir Path temp;

    @Test void watchedFilesApplyAfterDebounceWithAccurateNames() throws Exception {
        Files.createDirectories(temp.resolve("events"));
        Files.createDirectories(temp.resolve("functions/gifts"));
        Files.createDirectories(temp.resolve("scripts"));
        Files.writeString(temp.resolve("config.yml"), "tiktok:\n  unique-id: creator\n");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> changes = new AtomicReference<>();
        try (LiveFileWatcher watcher = new LiveFileWatcher(temp, true, 250L,
                files -> { changes.set(files); latch.countDown(); },
                failure -> { throw new AssertionError(failure); })) {
            watcher.start();
            Thread.sleep(800L); // establish watcher baseline
            Files.writeString(temp.resolve("functions/gifts/new.json"), "{\"schemaVersion\":1}");
            assertTrue(latch.await(6, TimeUnit.SECONDS),
                    "nested function files should trigger a reload");
            assertTrue(changes.get().contains("functions/gifts/new.json"), changes.toString());
        }
    }

    @Test void backupsAndTemporaryFilesDoNotTriggerReloads() throws Exception {
        Files.createDirectories(temp.resolve("events"));
        Files.writeString(temp.resolve("config.yml"), "test: true\n");
        AtomicInteger calls = new AtomicInteger();
        try (LiveFileWatcher watcher = new LiveFileWatcher(temp, true, 250L,
                files -> calls.incrementAndGet(),
                failure -> { throw new AssertionError(failure); })) {
            watcher.start();
            Thread.sleep(800L);
            Files.writeString(temp.resolve("events/rules.yml.bak-123"), "rules: []");
            Files.writeString(temp.resolve("events/.rules.yml.swp"), "not final");
            Files.writeString(temp.resolve("events/rules.tmp"), "not final");
            Thread.sleep(1400L);
            assertEquals(0, calls.get());
        }
    }

    @Test void hotReloadConfigCanBeDisabledAndDebounced() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        yaml.set("hot-reload.enabled", false);
        yaml.set("hot-reload.debounce-ms", 1250);
        PluginConfig config = PluginConfig.load(yaml).config();
        assertFalse(config.hotReloadEnabled());
        assertEquals(1250, config.hotReloadDebounceMillis());
    }
}
