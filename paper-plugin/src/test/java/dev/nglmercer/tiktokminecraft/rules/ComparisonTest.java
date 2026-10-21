package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonTest {

    @Test void equalityComparesNumbersNumerically() {
        assertTrue(Comparison.test(Comparison.Operator.EQUALS, 5L, 5));
        assertTrue(Comparison.test(Comparison.Operator.EQUALS, "5", 5L));
        assertFalse(Comparison.test(Comparison.Operator.EQUALS, 5L, 6L));
    }

    @Test void equalityIgnoresCaseWhenAsked() {
        assertTrue(Comparison.test(Comparison.Operator.EQUALS_IGNORE_CASE, "!HELLO", "!hello"));
        assertFalse(Comparison.test(Comparison.Operator.EQUALS, "!HELLO", "!hello"));
    }

    @Test void textComparisons() {
        assertTrue(Comparison.test(Comparison.Operator.CONTAINS, "hello world", "lo wo"));
        assertTrue(Comparison.test(Comparison.Operator.STARTS_WITH, "hello", "he"));
        assertTrue(Comparison.test(Comparison.Operator.ENDS_WITH, "hello", "lo"));
        assertFalse(Comparison.test(Comparison.Operator.CONTAINS, "hello", "xyz"));
    }

    @Test void numericComparisons() {
        assertTrue(Comparison.test(Comparison.Operator.GREATER_THAN, 10L, 5L));
        assertTrue(Comparison.test(Comparison.Operator.GREATER_OR_EQUAL, 5L, 5L));
        assertTrue(Comparison.test(Comparison.Operator.LESS_THAN, 4L, 5L));
        assertTrue(Comparison.test(Comparison.Operator.LESS_OR_EQUAL, 5L, 5L));
        assertFalse(Comparison.test(Comparison.Operator.GREATER_THAN, "abc", 5L));
    }

    @Test void inComparesAgainstAList() {
        assertTrue(Comparison.test(
                Comparison.Operator.IN, "Rose", Arrays.asList("Rose", "Tulip")));
        assertFalse(Comparison.test(
                Comparison.Operator.IN, "Daisy", Arrays.asList("Rose", "Tulip")));
    }

    @Test void unknownOperatorIsRejected() {
        assertNull(Comparison.parse("matches"));
        assertEquals(Comparison.Operator.EQUALS, Comparison.parse("equals"));
        assertEquals(Comparison.Operator.EQUALS_IGNORE_CASE, Comparison.parse("EQUALS-IGNORE-CASE"));
    }

    @Test void fieldAliasesResolveToTheNormalizedSchema() {
        assertEquals("chat.text", EventFields.resolve("text"));
        assertEquals("gift.name", EventFields.resolve("gift-name"));
        assertEquals("user.nickname", EventFields.resolve("nickname"));
        assertEquals("chat.text", EventFields.resolve("chat.text"));
        assertTrue(EventFields.isKnown("text"));
        assertFalse(EventFields.isKnown("nonsense"));
    }

    @Test void fieldsAreReadFromTheEvent() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", "Oyeee");
        payload.put("gift.name", "Rose");
        TikTokEventContext event =
                new TikTokEventContext(
                        "id-1", "room-1", TikTokEventContext.CHAT, "42", "cjr", "CJ.~",
                        1000L, false, payload);
        assertEquals("Oyeee", EventFields.read(event, "text"));
        assertEquals("CJ.~", EventFields.read(event, "user.nickname"));
        assertEquals("CJ.~", EventFields.read(event, "nickname"));
        assertEquals("chat", EventFields.read(event, "event.type"));
        // A field the event does not carry has no value.
        assertNull(EventFields.read(event, "like.count"));
    }

    @Test void durationsAreParsed() {
        assertEquals(10000L, Durations.parseMillis("10s"));
        assertEquals(120000L, Durations.parseMillis("2m"));
        assertEquals(500L, Durations.parseMillis("500ms"));
        assertEquals(3600000L, Durations.parseMillis("1h"));
        assertEquals(250L, Durations.parseMillis("250"));
        assertEquals(-1L, Durations.parseMillis("soon"));
        assertEquals(-1L, Durations.parseMillis(""));
    }
}
