package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Placeholders in action text render from the live event. */
class EventTemplateTest {
    private static TikTokEventContext gift(String nickname, String uniqueId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("gift.id", "5655");
        payload.put("gift.name", "Rose");
        payload.put("gift.diamonds", 1L);
        return new TikTokEventContext("event-1", "room-1", "gift.final",
                "user-1", uniqueId, nickname, 123L, false, payload);
    }

    @Test void rendersGiftAndViewer() {
        TikTokEventContext event = gift("SomeUser", "someuser");
        assertEquals("Rose!", EventTemplate.render("{gift.name}!", event));
        assertEquals("Rose!", EventTemplate.render("{gift-name}!", event));
        assertEquals("Thanks SomeUser!",
                EventTemplate.render("Thanks {viewer}!", event));
        assertEquals("Thanks SomeUser!",
                EventTemplate.render("Thanks {nickname}!", event));
        assertEquals("Thanks someuser!",
                EventTemplate.render("Thanks {user.uniqueId}!", event));
    }

    @Test void viewerFallsBackToUsername() {
        assertEquals("Thanks @someuser!",
                EventTemplate.render("Thanks {viewer}!", gift("", "someuser")));
        assertEquals("Thanks @someuser!",
                EventTemplate.render("Thanks {viewer}!", gift(null, "someuser")));
        assertEquals("Thanks unknown!",
                EventTemplate.render("Thanks {viewer}!", gift("", "")));
    }

    @Test void missingFieldRendersEmptyAndUnknownStaysLiteral() {
        TikTokEventContext event = gift("SomeUser", "someuser");
        assertEquals("likes: ",
                EventTemplate.render("likes: {like.count}", event));
        assertEquals("keeps {nope} as-is",
                EventTemplate.render("keeps {nope} as-is", event));
        assertEquals("keeps {} as-is",
                EventTemplate.render("keeps {} as-is", event));
        assertEquals("keeps {gift.name as-is",
                EventTemplate.render("keeps {gift.name as-is", event));
    }

    @Test void nullEventKeepsTemplateAndNullTemplateRendersEmpty() {
        assertEquals("{gift.name}!", EventTemplate.render("{gift.name}!", null));
        assertEquals("", EventTemplate.render(null, gift("SomeUser", "someuser")));
    }
}
