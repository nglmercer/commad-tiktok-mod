package dev.nglmercer.tiktokminecraft.functions;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Resolves relative coordinates in a command against a target location.
 *
 * <p>A function step such as {@code summon minecraft:zombie ~ ~1 ~} is
 * meaningless when it is executed as the console: the console's position is
 * the world spawn, so the entity would appear in the wrong place. Before such
 * a command is dispatched, its relative coordinates are rewritten to absolute
 * ones derived from the target player.</p>
 *
 * <p>Both the world-relative form ({@code ~}) and the local form
 * ({@code ^}) are supported. Text inside a quoted argument is never
 * touched.</p>
 */
public final class CommandTargetResolver {

    private CommandTargetResolver() {
    }

    /**
     * Rewrites the relative coordinates of a command.
     *
     * @param command the command as compiled by the function engine
     * @param origin where the relative coordinates are resolved against
     * @return the rewritten command, or the original when nothing changed
     */
    public static String resolve(String command, Location origin) {
        if (command == null || origin == null || command.indexOf('~') < 0
                && command.indexOf('^') < 0) {
            return command;
        }
        List<String> tokens = tokenize(command);
        List<String> rewritten = new ArrayList<String>(tokens.size());
        boolean changed = false;
        int index = 0;
        while (index < tokens.size()) {
            if (index + 2 < tokens.size()
                    && isRelative(tokens.get(index))
                    && isRelative(tokens.get(index + 1))
                    && isRelative(tokens.get(index + 2))) {
                double[] resolved = resolveTriple(
                        tokens.get(index),
                        tokens.get(index + 1),
                        tokens.get(index + 2),
                        origin);
                rewritten.add(format(resolved[0]));
                rewritten.add(format(resolved[1]));
                rewritten.add(format(resolved[2]));
                changed = true;
                index += 3;
                continue;
            }
            rewritten.add(tokens.get(index));
            index++;
        }
        if (!changed) {
            return command;
        }
        return String.join(" ", rewritten);
    }

    /** @return true when the command uses a relative coordinate */
    public static boolean usesRelativeCoordinates(String command) {
        return command != null && (command.indexOf('~') >= 0 || command.indexOf('^') >= 0);
    }

    private static double[] resolveTriple(String x, String y, String z, Location origin) {
        double offsetX = parseOffset(x);
        double offsetY = parseOffset(y);
        double offsetZ = parseOffset(z);
        boolean local = x.startsWith("^") && y.startsWith("^") && z.startsWith("^");
        if (!local) {
            // World relative: the offset is added to the target's position.
            return new double[] {
                origin.getX() + offsetX,
                origin.getY() + offsetY,
                origin.getZ() + offsetZ
            };
        }
        double yaw = Math.toRadians(origin.getYaw());
        double pitch = Math.toRadians(origin.getPitch());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        // Minecraft's local axes: forward, left and up, derived from the
        // player's yaw and pitch.
        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = cosYaw * cosPitch;
        double leftX = -cosYaw;
        double leftY = 0.0;
        double leftZ = sinYaw;
        double upX = -sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = cosYaw * sinPitch;
        return new double[] {
            origin.getX() + leftX * offsetX + upX * offsetY + forwardX * offsetZ,
            origin.getY() + leftY * offsetX + upY * offsetY + forwardY * offsetZ,
            origin.getZ() + leftZ * offsetX + upZ * offsetY + forwardZ * offsetZ
        };
    }

    private static double parseOffset(String token) {
        String body = token.substring(1);
        if (body.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(body);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static boolean isRelative(String token) {
        if (token == null || token.length() < 1) {
            return false;
        }
        char first = token.charAt(0);
        if (first != '~' && first != '^') {
            return false;
        }
        String body = token.substring(1);
        if (body.isEmpty()) {
            return true;
        }
        if (body.charAt(0) == '-' || body.charAt(0) == '+') {
            body = body.substring(1);
        }
        if (body.isEmpty()) {
            return true;
        }
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (character != '.' && (character < '0' || character > '9')) {
                return false;
            }
        }
        return true;
    }

    private static String format(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    /**
     * Splits a command into tokens, keeping quoted arguments intact.
     */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < command.length(); index++) {
            char character = command.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }
            if (!quoted && character == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** @return the direction the player is looking at */
    public static Vector facing(Location origin) {
        return origin.getDirection();
    }
}
