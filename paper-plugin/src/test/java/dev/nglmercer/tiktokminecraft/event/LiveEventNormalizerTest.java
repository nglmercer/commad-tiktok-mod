package dev.nglmercer.tiktokminecraft.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nglmercer.tiktoklive.ChatEvent;
import io.github.nglmercer.tiktoklive.EmoteChatEvent;
import io.github.nglmercer.tiktoklive.EventUser;
import io.github.nglmercer.tiktoklive.GiftEvent;
import io.github.nglmercer.tiktoklive.LikeEvent;
import io.github.nglmercer.tiktoklive.LiveEmote;
import io.github.nglmercer.tiktoklive.SocialEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveEventNormalizerTest {

    private static final EventUser USER =
            new EventUser("12345", "CJ.~", "cjr", "SECUID", "https://example.invalid/a.jpg");

    @Test void chatKeepsTextAndUser() {
        ChatEvent event =
                new ChatEvent("WebcastChatMessage", "msg-1", false, USER, "Oyeee", List.of());
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");

        assertEquals(TikTokEventContext.CHAT, context.type());
        assertEquals("msg-1", context.eventId());
        assertEquals("room-1", context.roomId());
        assertEquals("CJ.~", context.nickname());
        assertEquals("cjr", context.uniqueId());
        assertEquals("12345", context.userId());
        assertEquals("Oyeee", context.chatText());
        assertFalse(context.isHistory());
    }

    @Test void chatPreservesUnicodeAndEmoji() {
        ChatEvent event =
                new ChatEvent(
                        "WebcastChatMessage",
                        "msg-2",
                        false,
                        new EventUser("1", "diamante pequeño 🫩", "u", "s", ""),
                        "padre ay un easter egg 🇦🇷",
                        List.of());
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");

        assertEquals("padre ay un easter egg 🇦🇷", context.chatText());
        assertEquals("diamante pequeño 🫩", context.nickname());
    }

    @Test void emoteOnlyMessageGetsItsOwnTrigger() {
        EmoteChatEvent event =
                new EmoteChatEvent(
                        "WebcastEmoteChatMessage",
                        "msg-3",
                        false,
                        USER,
                        List.of(new LiveEmote("7", "[laughcry]", "https://example.invalid/e.png", 0, 0, "")));
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");

        // An emote-only message has no body, so it must not be published as a
        // chat message with empty text.
        assertEquals(TikTokEventContext.CHAT_EMOTE, context.type());
        assertEquals("", context.chatText());
        assertEquals(1, context.chatEmotes().size());
    }

    @Test void giftExposesTheDocumentedFields() {
        GiftEvent event =
                new GiftEvent(
                        "WebcastGiftMessage",
                        "msg-4",
                        false,
                        USER,
                        null,
                        "5655",
                        "Rose",
                        1L,
                        3L,
                        7L,
                        "group-9",
                        true,
                        "https://example.invalid/g.png",
                        Boolean.TRUE);
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");

        assertEquals(TikTokEventContext.GIFT, context.type());
        assertEquals("5655", context.giftId());
        assertEquals("Rose", context.giftName());
        assertEquals(1L, context.giftDiamonds());
        assertEquals(3L, context.giftRepeatCount());
        assertEquals(7L, context.giftComboCount());
        assertEquals("group-9", context.giftGroupId());
        assertTrue(context.giftRepeatEnd());
    }

    @Test void likeExposesCountAndTotalSeparately() {
        LikeEvent event = new LikeEvent("WebcastLikeMessage", "msg-5", false, USER, 12L, 340L);
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");

        assertEquals(TikTokEventContext.LIKE, context.type());
        assertEquals(12L, context.likeCount());
        assertEquals(340L, context.likeTotal());
    }

    @Test void confirmedFollowAndShareAreClassified() {
        TikTokEventContext follow = LiveEventNormalizer.normalize(social(1L), "room-1");
        assertEquals(TikTokEventContext.SOCIAL_FOLLOW, follow.type());
        assertEquals("follow", follow.socialKind());

        TikTokEventContext share = LiveEventNormalizer.normalize(social(3L), "room-1");
        assertEquals(TikTokEventContext.SOCIAL_SHARE, share.type());
    }

    @Test void unconfirmedSocialActionsAreNeverFoldedIntoFollowOrShare() {
        TikTokEventContext other = LiveEventNormalizer.normalize(social(99L), "room-1");
        assertEquals(TikTokEventContext.SOCIAL_OTHER, other.type());
        assertFalse(TikTokEventContext.SOCIAL_FOLLOW.equals(other.type()));
        assertFalse(TikTokEventContext.SOCIAL_SHARE.equals(other.type()));

        TikTokEventContext unknown = LiveEventNormalizer.normalize(social(0L), "room-1");
        assertEquals(TikTokEventContext.SOCIAL_UNKNOWN, unknown.type());
        assertFalse(TikTokEventContext.SOCIAL_FOLLOW.equals(unknown.type()));
        assertFalse(TikTokEventContext.SOCIAL_SHARE.equals(unknown.type()));
    }

    @Test void historyFlagIsPreserved() {
        ChatEvent event =
                new ChatEvent("WebcastChatMessage", "msg-6", true, USER, "old", List.of());
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");
        assertTrue(context.isHistory());
    }

    @Test void missingUserBecomesEmptyFields() {
        ChatEvent event =
                new ChatEvent("WebcastChatMessage", "msg-7", false, null, "hi", List.of());
        TikTokEventContext context = LiveEventNormalizer.normalize(event, "room-1");
        assertEquals("", context.nickname());
        assertEquals("", context.uniqueId());
        assertEquals("hi", context.chatText());
    }

    private static SocialEvent social(long action) {
        return new SocialEvent(
                "WebcastSocialMessage",
                "msg-social",
                false,
                USER,
                action,
                5L,
                3L,
                0L,
                "",
                0L,
                0L,
                "",
                "");
    }
}
