package dev.nglmercer.tiktokminecraft.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Formats untrusted TikTok text for display.
 *
 * <p>Every string that reaches Minecraft is built with
 * {@link Component#text(String)}. That API never parses markup, so a message
 * containing {@code <red>} or {@code $} is displayed literally and can never
 * inject formatting or a MiniMessage tag.</p>
 */
public final class ChatFormatter {

    private ChatFormatter() {
    }

    /**
     * The display name of a viewer.
     *
     * @return the nickname, the unique id, or {@code unknown} when neither is
     *     available
     */
    public static String displayName(String nickname, String uniqueId) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        if (uniqueId != null && !uniqueId.trim().isEmpty()) {
            return "@" + uniqueId.trim();
        }
        return "unknown";
    }

    /**
     * Truncates text to at most {@code maxLength} characters without splitting
     * a surrogate pair or a combining sequence.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= maxLength) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxLength);
        return text.substring(0, end);
    }

    /** The plain-text console line for a chat message. */
    public static String consoleLine(String nickname, String uniqueId, String text) {
        return "Chat " + displayName(nickname, uniqueId) + ": " + safe(text);
    }

    /** The in-game component for a chat message. */
    public static Component chatComponent(
            String prefix, String nickname, String uniqueId, String text) {
        Component result = Component.empty();
        if (prefix != null && !prefix.isEmpty()) {
            result = result.append(Component.text(prefix + " "));
        }
        return result.append(
                Component.text(displayName(nickname, uniqueId) + ": " + safe(text)));
    }

    /** A component that shows text exactly as given. */
    public static Component plain(String text) {
        return Component.text(safe(text));
    }

    /** The plain text of a component. */
    public static String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Removes control characters that would corrupt a console line. */
    public static String safe(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n' || character == '\r' || character == '\t') {
                builder.append(' ');
            } else if (character < 0x20 || character == 0x7f) {
                continue;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
