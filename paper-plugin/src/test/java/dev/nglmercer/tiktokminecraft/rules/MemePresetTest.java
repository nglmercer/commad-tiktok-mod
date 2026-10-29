package dev.nglmercer.tiktokminecraft.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Guard against accidentally disabling or misidentifying the meme panel. */
class MemePresetTest {
    private Map<String, Map<?, ?>> loadRules() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "defaults/events/interactions.yml")) {
            assertNotNull(input, "The meme interactions resource must be packaged");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, Map<?, ?>> rules = new HashMap<>();
            for (Map<?, ?> rule : yaml.getMapList("rules")) {
                assertEquals(Boolean.TRUE, rule.get("enabled"),
                        "All meme examples are enabled: " + rule.get("id"));
                rules.put(String.valueOf(rule.get("id")), rule);
            }
            return rules;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> firstAction(Map<?, ?> rule) {
        assertNotNull(rule);
        List<Map<?, ?>> actions = (List<Map<?, ?>>) rule.get("actions");
        assertTrue(!actions.isEmpty());
        return actions.get(0);
    }

    @SuppressWarnings("unchecked")
    private void assertGift(Map<String, Map<?, ?>> rules, String id, String giftId, String action) {
        Map<?, ?> rule = rules.get(id);
        assertNotNull(rule, id);
        assertEquals("gift.final", rule.get("trigger"));
        assertEquals(giftId, ((Map<String, Object>) rule.get("match")).get("gift-id"));
        assertEquals(action, firstAction(rule).get("type"));
    }

    @Test void theMemePresetContainsAllActiveInteractions() throws Exception {
        Map<String, Map<?, ?>> rules = loadRules();
        assertEquals(14, rules.size());

        assertGift(rules, "meme-rose-charged-creeper-cage", "5655", "block-cage");
        assertGift(rules, "meme-confetti-tnt", "5585", "prime-tnt");
        assertGift(rules, "meme-rosa-charged-creeper", "8913", "spawn-entity");
        assertGift(rules, "meme-finger-heart-webs", "5487", "place-cobweb");
        assertGift(rules, "meme-cap-wardens", "6104", "spawn-entity");
        assertGift(rules, "meme-corgi-inventory", "6267", "temporary-clear-inventory");
        assertGift(rules, "meme-capybara-evokers", "14488", "spawn-entity");
        assertGift(rules, "meme-doughnut-void", "5879", "void-shaft");
        assertGift(rules, "meme-fireworks-skeletons", "6090", "spawn-entity");
        assertGift(rules, "meme-money-gun-withers", "7168", "spawn-entity");

        assertEquals("minecraft:evoker",
                firstAction(rules.get("meme-capybara-evokers")).get("entity"));
        assertEquals(15, firstAction(rules.get("meme-capybara-evokers")).get("count"));
        assertEquals(4096, firstAction(rules.get("meme-doughnut-void")).get("max-blocks"));
        assertEquals("like", rules.get("meme-likes-zombie").get("trigger"));
        assertEquals("social.share", rules.get("meme-share-apples").get("trigger"));
        assertEquals("block-cage",
                firstAction(rules.get("meme-high-value-tnt-cage")).get("type"));
        assertEquals("minecraft:tnt",
                firstAction(rules.get("meme-high-value-tnt-cage")).get("material"));
        Map<?, ?> rose = firstAction(rules.get("meme-rose-charged-creeper-cage"));
        assertEquals("minecraft:tnt", rose.get("material"));
        assertEquals(1, rose.get("radius"));
        assertEquals("minecraft:creeper", rose.get("occupant"));
        assertEquals(Boolean.TRUE, rose.get("occupant-charged"));
        assertEquals(Boolean.FALSE, rose.get("ignite"));
        assertEquals("gift.final", rules.get("meme-high-value-tnt-cage").get("trigger"));
        Map<?, ?> thanks = rules.get("meme-gift-thanks-title");
        assertNotNull(thanks, "meme-gift-thanks-title");
        assertEquals("gift.final", thanks.get("trigger"));
        assertTrue(!thanks.containsKey("match"),
                "The thanks title fires for every gift");
        assertEquals("title", firstAction(thanks).get("type"));
        assertEquals("{gift.name}!", firstAction(thanks).get("title"));
        assertEquals("Thanks {viewer}!", firstAction(thanks).get("subtitle"));
    }

    @Test void theNewActionsAreRegistered() {
        ActionRegistry registry = new ActionRegistry();
        assertTrue(registry.contains("tnt-cage"));
        assertTrue(registry.contains("block-cage"));
        assertEquals(registry.get("tnt-cage"), registry.get("block-cage"),
                "Cage aliases must share restoration state");
        assertTrue(registry.contains("void-shaft"));
        assertTrue(registry.contains("prime-tnt"));
        assertTrue(registry.contains("chunk-replace"));
    }
}
