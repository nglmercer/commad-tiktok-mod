package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.chat.ChatFormatter;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;

/**
 * Renders {@code {field}} placeholders in action text from the live event.
 *
 * <p>The name inside the braces is any documented event field or alias, for
 * example {@code {gift.name}}, {@code {gift-name}}, {@code {user.nickname}}
 * or {@code {nickname}. {@code {viewer}} is the friendly display name: the
 * nickname, {@code @username} when no nickname is set, or {@code unknown}.
 * A field the event does not carry renders as an empty string, while an
 * unknown name is left untouched so typos stay visible.</p>
 */
public final class EventTemplate {

    private EventTemplate() {
    }

    /**
     * @return the template with every known placeholder replaced; the
     *     template itself when there is no event to read from
     */
    public static String render(String template, TikTokEventContext event) {
        if (template == null) {
            return "";
        }
        if (template.isEmpty() || event == null) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf('{', cursor);
            if (open < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            out.append(template, cursor, open);
            String name = template.substring(open + 1, close).trim();
            String value = name.isEmpty() ? null : value(name, event);
            out.append(value == null ? template.substring(open, close + 1) : value);
            cursor = close + 1;
        }
        return out.toString();
    }

    /** @return the replacement, or null when the placeholder stays literal */
    private static String value(String name, TikTokEventContext event) {
        if ("viewer".equalsIgnoreCase(name)) {
            return ChatFormatter.displayName(event.nickname(), event.uniqueId());
        }
        if (!EventFields.isKnown(name)) {
            return null;
        }
        Object read = EventFields.read(event, name);
        return read == null ? "" : String.valueOf(read);
    }
}
