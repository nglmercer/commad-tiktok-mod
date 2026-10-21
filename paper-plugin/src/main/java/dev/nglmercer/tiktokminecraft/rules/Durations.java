package dev.nglmercer.tiktokminecraft.rules;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the duration strings a rule file uses. */
public final class Durations {

    private static final Pattern PATTERN =
            Pattern.compile("^(\\d+)(ms|s|m|h|d)?$", Pattern.CASE_INSENSITIVE);

    private Durations() {
    }

    /**
     * Parses a duration.
     *
     * @param raw a number of milliseconds, or a number with a unit
     * @return the duration in milliseconds, or -1 when the text is not a valid
     *     duration
     */
    public static long parseMillis(String raw) {
        if (raw == null) {
            return -1;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return -1;
        }
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) {
            return -1;
        }
        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException failure) {
            return -1;
        }
        String unit = matcher.group(2);
        if (unit == null) {
            return value;
        }
        switch (unit) {
            case "ms":
                return value;
            case "s":
                return value * 1000L;
            case "m":
                return value * 60_000L;
            case "h":
                return value * 3_600_000L;
            case "d":
                return value * 86_400_000L;
            default:
                return -1;
        }
    }
}
