package dev.nglmercer.tiktokminecraft.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MultiAccountConfigTest {
    private YamlConfiguration base() {
        var yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "@host");
        return yaml;
    }

    @Test void legacyConfigurationStillHasOneConnection() {
        var result = PluginConfig.load(base());
        assertTrue(result.isValid());
        assertEquals(List.of(new PluginConfig.Account("host", "")), result.config().accounts());
    }

    @Test void collaboratorsKeepTheirOwnTargets() {
        var yaml = base();
        yaml.set("tiktok.target-player", "HostPlayer");
        yaml.set("tiktok.accounts", List.of(
                Map.of("unique-id", "@friend", "target-player", "FriendPlayer"),
                Map.of("unique-id", "other")));
        var result = PluginConfig.load(yaml);
        assertTrue(result.isValid(), result.problems().toString());
        assertEquals(List.of(new PluginConfig.Account("host", "HostPlayer"),
                new PluginConfig.Account("friend", "FriendPlayer"),
                new PluginConfig.Account("other", "")), result.config().accounts());
    }

    @Test void duplicatesAndInvalidTargetsAreRejected() {
        var yaml = base();
        yaml.set("tiktok.accounts", List.of(Map.of("unique-id", "HOST")));
        assertFalse(PluginConfig.load(yaml).isValid());
        yaml.set("tiktok.accounts", List.of(Map.of("unique-id", "friend", "target-player", "@a")));
        assertFalse(PluginConfig.load(yaml).isValid());
    }

    @Test void malformedAccountsCannotSilentlyDisappear() {
        var yaml = base();
        yaml.set("tiktok.accounts", List.of("friend"));
        assertFalse(PluginConfig.load(yaml).isValid());
        yaml.set("tiktok.accounts", "friend");
        assertFalse(PluginConfig.load(yaml).isValid());
    }
}
