package dev.nglmercer.tiktokminecraft.event;

import io.github.nglmercer.tiktoklive.ChatEvent;
import io.github.nglmercer.tiktoklive.EmoteChatEvent;
import io.github.nglmercer.tiktoklive.EventUser;
import io.github.nglmercer.tiktoklive.GiftEvent;
import io.github.nglmercer.tiktoklive.LikeEvent;
import io.github.nglmercer.tiktoklive.LiveEmote;
import io.github.nglmercer.tiktoklive.LiveEvent;
import io.github.nglmercer.tiktoklive.MemberEvent;
import io.github.nglmercer.tiktoklive.RoomUserEvent;
import io.github.nglmercer.tiktoklive.SocialEvent;
import io.github.nglmercer.tiktoklive.SocialKind;
import io.github.nglmercer.tiktoklive.TopViewer;
import io.github.nglmercer.tiktoklive.UnknownEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts SDK events into the normalized {@link TikTokEventContext}.
 *
 * <p>This is the only place that knows about the SDK's event shapes. It never
 * stores protobuf objects: every value is copied into a primitive payload so
 * a rule engine cannot accidentally mutate transport state.</p>
 */
public final class LiveEventNormalizer {

    private LiveEventNormalizer() {
    }

    /** Normalizes one SDK event. */
    public static TikTokEventContext normalize(LiveEvent event, String roomId) {
        if (event == null) {
            return unknown("", roomId, null, null, false, "null event");
        }
        if (event instanceof ChatEvent) {
            return chat((ChatEvent) event, roomId);
        }
        if (event instanceof EmoteChatEvent) {
            return emoteChat((EmoteChatEvent) event, roomId);
        }
        if (event instanceof GiftEvent) {
            return gift((GiftEvent) event, roomId);
        }
        if (event instanceof LikeEvent) {
            return like((LikeEvent) event, roomId);
        }
        if (event instanceof MemberEvent) {
            return member((MemberEvent) event, roomId);
        }
        if (event instanceof SocialEvent) {
            return social((SocialEvent) event, roomId);
        }
        if (event instanceof RoomUserEvent) {
            return roomUser((RoomUserEvent) event, roomId);
        }
        if (event instanceof UnknownEvent) {
            return unknown(event.msgId(), roomId, null, null, event.isHistory(), event.method());
        }
        return unknown(event.msgId(), roomId, null, null, event.isHistory(), event.method());
    }

    private static TikTokEventContext chat(ChatEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", safe(event.comment()));
        payload.put("chat.emotes", emoteList(event.emotes()));
        payload.put("chat.emoteOnly", Boolean.FALSE);
        return new TikTokEventContext(
                id(event.msgId(), "chat"),
                roomId,
                TikTokEventContext.CHAT,
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext emoteChat(EmoteChatEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        // An emote-only message carries no text. It gets its own trigger so it
        // is never published as an empty chat line.
        payload.put("chat.text", "");
        payload.put("chat.emotes", emoteList(event.emotes()));
        payload.put("chat.emoteOnly", Boolean.TRUE);
        return new TikTokEventContext(
                id(event.msgId(), "emote"),
                roomId,
                TikTokEventContext.CHAT_EMOTE,
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext gift(GiftEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("gift.id", safe(event.giftId()));
        payload.put("gift.name", safe(event.giftName()));
        payload.put("gift.diamonds", event.diamondCount());
        payload.put("gift.repeatCount", event.repeatCount());
        payload.put("gift.comboCount", event.comboCount());
        payload.put("gift.groupId", safe(event.groupId()));
        payload.put("gift.repeatEnd", Boolean.valueOf(event.repeatEnd()));
        payload.put("gift.streakable", Boolean.valueOf(Boolean.TRUE.equals(event.streakable())));
        return new TikTokEventContext(
                id(event.msgId(), "gift"),
                roomId,
                TikTokEventContext.GIFT,
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext like(LikeEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("like.count", event.count());
        payload.put("like.total", event.total());
        return new TikTokEventContext(
                id(event.msgId(), "like"),
                roomId,
                TikTokEventContext.LIKE,
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext member(MemberEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("member.count", event.memberCount());
        payload.put("member.action", event.action());
        return new TikTokEventContext(
                id(event.msgId(), "member"),
                roomId,
                TikTokEventContext.MEMBER,
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext social(SocialEvent event, String roomId) {
        SocialKind kind = event.kind();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("social.kind", kind.wireName());
        payload.put("social.action", event.action());
        payload.put("social.followCount", event.followCount());
        payload.put("social.shareCount", event.shareCount());
        payload.put("social.shareType", event.shareType());
        payload.put("social.followType", event.followType());
        payload.put("social.scene", safe(event.scene()));
        payload.put("social.shareTarget", safe(event.shareTarget()));
        payload.put("social.targetUserId", safe(event.targetUserId()));
        return new TikTokEventContext(
                id(event.msgId(), "social"),
                roomId,
                socialType(kind),
                userId(event.user()),
                uniqueId(event.user()),
                nickname(event.user()),
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    /**
     * Maps a social classification to a trigger name.
     *
     * <p>An unknown or unconfirmed social action is never folded into a
     * follow or a share: {@code social.other} and {@code social.unknown} stay
     * separate so a rule can only match what the SDK actually confirmed.</p>
     */
    public static String socialType(SocialKind kind) {
        if (kind == null) {
            return TikTokEventContext.SOCIAL_UNKNOWN;
        }
        switch (kind) {
            case FOLLOW:
                return TikTokEventContext.SOCIAL_FOLLOW;
            case SHARE:
                return TikTokEventContext.SOCIAL_SHARE;
            case OTHER:
                return TikTokEventContext.SOCIAL_OTHER;
            case UNKNOWN:
            default:
                return TikTokEventContext.SOCIAL_UNKNOWN;
        }
    }

    private static TikTokEventContext roomUser(RoomUserEvent event, String roomId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("room.viewers", event.viewers());
        payload.put("room.popularity", event.popularity());
        payload.put("room.totalUser", event.totalUser());
        payload.put("room.anonymous", event.anonymous());
        List<String> viewers = new ArrayList<String>();
        for (TopViewer viewer : event.topViewers()) {
            viewers.add(nickname(viewer.user()));
        }
        payload.put("room.topViewers", viewers);
        return new TikTokEventContext(
                id(event.msgId(), "room"),
                roomId,
                TikTokEventContext.ROOM_USER,
                "",
                "",
                "",
                System.currentTimeMillis(),
                event.isHistory(),
                payload);
    }

    private static TikTokEventContext unknown(
            String msgId, String roomId, String userId, String uniqueId, boolean history,
            String method) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("unknown.method", safe(method));
        return new TikTokEventContext(
                id(msgId, "unknown"), roomId, TikTokEventContext.UNKNOWN, userId, uniqueId, "",
                System.currentTimeMillis(), history, payload);
    }

    private static String id(String msgId, String prefix) {
        String text = safe(msgId);
        return text.isEmpty() ? prefix + "-" + System.nanoTime() : text;
    }

    private static String userId(EventUser user) {
        return user == null ? "" : safe(user.userId());
    }

    private static String uniqueId(EventUser user) {
        return user == null ? "" : safe(user.uniqueId());
    }

    private static String nickname(EventUser user) {
        return user == null ? "" : safe(user.nickname());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<Object> emoteList(List<LiveEmote> emotes) {
        List<Object> result = new ArrayList<Object>();
        if (emotes == null) {
            return result;
        }
        for (LiveEmote emote : emotes) {
            if (emote == null) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            copy.put("id", safe(emote.id()));
            copy.put("shortcode", safe(emote.shortcode()));
            copy.put("imageUrl", safe(emote.imageUrl()));
            copy.put("index", emote.index());
            copy.put("emoteType", emote.emoteType());
            copy.put("packageId", safe(emote.packageId()));
            result.add(copy);
        }
        return result;
    }
}
