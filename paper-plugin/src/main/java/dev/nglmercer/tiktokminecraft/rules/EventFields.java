package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the normalized fields of an event by name.
 *
 * <p>Rules address fields with a dotted path such as {@code chat.text} or
 * {@code gift.name}. A small set of friendly aliases ({@code text},
 * {@code gift-name}, {@code nickname}) maps onto those paths so a rule file
 * stays readable.</p>
 */
public final class EventFields {

    private static final Map<String, String> ALIASES = new HashMap<String, String>();

    static {
        ALIASES.put("text", "chat.text");
        ALIASES.put("comment", "chat.text");
        ALIASES.put("emotes", "chat.emotes");
        ALIASES.put("emote-only", "chat.emoteOnly");
        ALIASES.put("gift-name", "gift.name");
        ALIASES.put("gift-id", "gift.id");
        ALIASES.put("diamonds", "gift.diamonds");
        ALIASES.put("gift-diamonds", "gift.diamonds");
        ALIASES.put("repeat-count", "gift.repeatCount");
        ALIASES.put("combo-count", "gift.comboCount");
        ALIASES.put("group-id", "gift.groupId");
        ALIASES.put("repeat-end", "gift.repeatEnd");
        ALIASES.put("like-count", "like.count");
        ALIASES.put("like-total", "like.total");
        ALIASES.put("kind", "social.kind");
        ALIASES.put("social-action", "social.action");
        ALIASES.put("follow-count", "social.followCount");
        ALIASES.put("share-count", "social.shareCount");
        ALIASES.put("nickname", "user.nickname");
        ALIASES.put("unique-id", "user.uniqueId");
        ALIASES.put("user-id", "user.id");
        ALIASES.put("member-count", "member.count");
        ALIASES.put("viewers", "room.viewers");
    }

    private EventFields() {
    }

    /** Resolves an alias to its canonical path, or returns the path itself. */
    public static String resolve(String field) {
        if (field == null) {
            return "";
        }
        String key = field.trim().toLowerCase(Locale.ROOT);
        String alias = ALIASES.get(key);
        return alias == null ? field.trim() : alias;
    }

    /** @return true when the field name is known to the normalized schema. */
    public static boolean isKnown(String field) {
        String path = resolve(field);
        return path.startsWith("event.")
                || path.startsWith("user.")
                || path.startsWith("chat.")
                || path.startsWith("gift.")
                || path.startsWith("like.")
                || path.startsWith("social.")
                || path.startsWith("room.")
                || path.startsWith("member.")
                || path.startsWith("unknown.");
    }

    /**
     * Reads a field.
     *
     * @return the value, or null when the event does not carry it
     */
    public static Object read(TikTokEventContext event, String field) {
        if (event == null || field == null) {
            return null;
        }
        String path = resolve(field);
        if (path.startsWith("event.")) {
            return readEvent(event, path.substring("event.".length()));
        }
        if (path.startsWith("user.")) {
            return readUser(event, path.substring("user.".length()));
        }
        return event.payload().get(path);
    }

    private static Object readEvent(TikTokEventContext event, String name) {
        if (name.equals("type")) {
            return event.type();
        }
        if (name.equals("id")) {
            return event.eventId();
        }
        if (name.equals("roomId")) {
            return event.roomId();
        }
        if (name.equals("timestamp")) {
            return Long.valueOf(event.timestamp());
        }
        if (name.equals("isHistory")) {
            return Boolean.valueOf(event.history());
        }
        return null;
    }

    private static Object readUser(TikTokEventContext event, String name) {
        if (name.equals("id")) {
            return event.userId();
        }
        if (name.equals("uniqueId")) {
            return event.uniqueId();
        }
        if (name.equals("nickname")) {
            return event.nickname();
        }
        return null;
    }
}
