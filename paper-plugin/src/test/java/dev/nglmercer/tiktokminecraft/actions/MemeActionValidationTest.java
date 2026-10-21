package dev.nglmercer.tiktokminecraft.actions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Verifies the validation guards for the configurable world prank actions. */
class MemeActionValidationTest {
    private static ActionContext context() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    @Test void tntCageValidatesRadiusHeightAndIgnition() {
        Action action = new TntCageAction();
        assertDoesNotThrow(() -> action.validate(new ActionSpec("tnt-cage",
                Map.of("radius", 2, "height", 3, "ignite", true,
                        "ignite-delay-ticks", 40, "fuse-ticks", 80,
                        "restore-seconds", 25)), context()));
        assertThrows(ActionException.class, () -> action.validate(
                new ActionSpec("tnt-cage", Map.of("radius", 50)), context()));
        assertThrows(ActionException.class, () -> action.validate(
                new ActionSpec("tnt-cage", Map.of("ignite", "yes")), context()));
    }

    @Test void blockCageAcceptsConfigurableSolidBlocks() {
        Action action = new BlockCageAction();
        assertDoesNotThrow(() -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:obsidian", "radius", 2,
                        "height", 3, "restore-seconds", 30)), context()));
        assertDoesNotThrow(() -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:glass", "radius", 1)), context()));
        assertDoesNotThrow(() -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:tnt", "radius", 1,
                        "occupant", "minecraft:creeper", "occupant-charged", true)), context()));
        assertThrows(ActionException.class, () -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:diamond_sword")), context()));
        assertThrows(ActionException.class, () -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:obsidian", "ignite", true)), context()));
        assertThrows(ActionException.class, () -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:glass", "occupant-charged", true)), context()));
        assertThrows(ActionException.class, () -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:glass", "radius", 6)), context()));
        assertThrows(ActionException.class, () -> action.validate(new ActionSpec("block-cage",
                Map.of("material", "minecraft:air")), context()));
    }

    @Test void voidShaftBoundsPerTickOperationsAndBlockChanges() {
        Action action = new VoidShaftAction();
        assertDoesNotThrow(() -> action.validate(new ActionSpec("void-shaft",
                Map.of("radius", 1, "max-blocks", 4096,
                        "blocks-per-tick", 96, "restore-seconds", 30)), context()));
        assertThrows(ActionException.class, () -> action.validate(
                new ActionSpec("void-shaft", Map.of("radius", 4)), context()));
        assertThrows(ActionException.class, () -> action.validate(
                new ActionSpec("void-shaft", Map.of("max-blocks", 100000)), context()));
        assertThrows(ActionException.class, () -> action.validate(
                new ActionSpec("void-shaft", Map.of("blocks-per-tick", 1000)), context()));
    }

    @Test void actionsAreDiscoverableByName() {
        ActionRegistry registry = new ActionRegistry();
        assertTrue(registry.contains("tnt-cage"));
        assertTrue(registry.contains("block-cage"));
        assertTrue(registry.contains("void-shaft"));
    }
}
