package dev.nglmercer.tiktokminecraft.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single normalized representation of a TikTok LIVE event.
 *
 * <p>Rule engines, actions and diagnostics only ever see this type, never a
 * protobuf message or an SDK event record. That keeps the rule surface stable
 * and prevents mutable transport objects from leaking into engine state.</p>
 *
 * <p>The payload keys are the documented normalized schema:</p>
 * <pre>
 * chat.text, chat.emotes
 * gift.id, gift.name, gift.diamonds, gift.repeatCount, gift.comboCount,
 * gift.groupId, gift.repeatEnd
 * like.count, like.total
 * social.kind, social.action, social.followCount, social.shareCount
 * room.viewers, room.totalUser
 * member.count
 * </pre>
 */
public record TikTokEventContext(
        String eventId,
        String roomId,
        String type,
        String userId,
        String uniqueId,
        String nickname,
        long timestamp,
        boolean history,
        Map<String, Object> payload) {

    /** Trigger names understood by the rule engine. */
    public static final String CHAT = "chat";
    /**
     * A standalone emote message: the viewer sent only graphical emotes and no
     * text. It is a distinct trigger because an emote-only message carries no
     * message body, and treating it as a chat message would publish an empty
     * line for every reaction in the room.
     */
    public static final String CHAT_EMOTE = "chat.emote";
    public static final String GIFT = "gift";
    public static final String GIFT_FINAL = "gift.final";
    public static final String LIKE = "like";
    public static final String MEMBER = "member";
    public static final String SOCIAL_FOLLOW = "social.follow";
    public static final String SOCIAL_SHARE = "social.share";
    public static final String SOCIAL_OTHER = "social.other";
    public static final String SOCIAL_UNKNOWN = "social.unknown";
    public static final String ROOM_USER = "room.user";
    public static final String UNKNOWN = "unknown";

    public TikTokEventContext {
        payload = payload == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(payload));
    }

    /** @return true when the event came from the room's history replay. */
    public boolean isHistory() {
        return history;
    }

    public String chatText() {
        return string("chat.text");
    }

    @SuppressWarnings("unchecked")
    public List<Object> chatEmotes() {
        Object raw = payload.get("chat.emotes");
        return raw instanceof List ? (List<Object>) raw : Collections.<Object>emptyList();
    }

    public String giftId() {
        return string("gift.id");
    }

    public String giftName() {
        return string("gift.name");
    }

    public long giftDiamonds() {
        return number("gift.diamonds");
    }

    public long giftRepeatCount() {
        return number("gift.repeatCount");
    }

    public long giftComboCount() {
        return number("gift.comboCount");
    }

    public String giftGroupId() {
        return string("gift.groupId");
    }

    public boolean giftRepeatEnd() {
        return Boolean.TRUE.equals(payload.get("gift.repeatEnd"));
    }

    public long likeCount() {
        return number("like.count");
    }

    public long likeTotal() {
        return number("like.total");
    }

    public String socialKind() {
        return string("social.kind");
    }

    public long socialAction() {
        return number("social.action");
    }

    public long socialFollowCount() {
        return number("social.followCount");
    }

    public long socialShareCount() {
        return number("social.shareCount");
    }

    public long roomViewers() {
        return number("room.viewers");
    }

    private String string(String key) {
        Object raw = payload.get(key);
        return raw == null ? "" : String.valueOf(raw);
    }

    private long number(String key) {
        Object raw = payload.get(key);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? 1L : 0L;
        }
        if (raw instanceof String) {
            try {
                return Long.parseLong(((String) raw).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
