package dev.nglmercer.tiktokminecraft.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class CommandTargetResolverTest {

    private static Location origin(double x, double y, double z, float yaw, float pitch) {
        Location location = new Location(null, x, y, z);
        location.setYaw(yaw);
        location.setPitch(pitch);
        return location;
    }

    @Test void relativeCoordinatesAreResolvedAgainstTheTarget() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String resolved = CommandTargetResolver.resolve(
                "/summon minecraft:zombie ~ ~1 ~", location);
        assertEquals("/summon minecraft:zombie 10.0 65.0 -5.0", resolved);
    }

    @Test void offsetsAreAddedToTheTarget() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String resolved = CommandTargetResolver.resolve(
                "/summon minecraft:zombie ~2 ~ ~-3", location);
        assertEquals("/summon minecraft:zombie 12.0 64.0 -8.0", resolved);
    }

    @Test void absoluteCoordinatesAreLeftAlone() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String command = "/summon minecraft:zombie 1 2 3";
        assertEquals(command, CommandTargetResolver.resolve(command, location));
    }

    @Test void quotedTextIsNeverTouched() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String command = "/title @a title \"hello ~ world\"";
        assertEquals(command, CommandTargetResolver.resolve(command, location));
    }

    @Test void aQuotedArgumentNextToCoordinatesIsNotConfused() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String resolved = CommandTargetResolver.resolve(
                "/title @a title \"a b\" ~ ~ ~", location);
        assertTrue(resolved.contains("\"a b\""), resolved);
        assertTrue(resolved.endsWith("10.0 64.0 -5.0"), resolved);
    }

    @Test void localCoordinatesFollowThePlayersOrientation() {
        // Looking south (yaw 0): local forward is +z, local left is -x.
        Location location = origin(0, 64, 0, 0f, 0f);
        String resolved = CommandTargetResolver.resolve(
                "/summon minecraft:zombie ^ ^ ^1", location);
        assertEquals("/summon minecraft:zombie 0.0 64.0 1.0", resolved);
    }

    @Test void relativeCoordinatesAreDetected() {
        assertTrue(CommandTargetResolver.usesRelativeCoordinates("/summon x ~ ~ ~"));
        assertFalse(CommandTargetResolver.usesRelativeCoordinates("/summon x 1 2 3"));
    }

    @Test void aCommandWithoutCoordinatesIsUnchanged() {
        Location location = origin(10, 64, -5, 0f, 0f);
        String command = "/say hello world";
        assertEquals(command, CommandTargetResolver.resolve(command, location));
    }
}
