package dev.nglmercer.tiktokminecraft.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.rules.EventRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DslMatchingTest {

    private static ActionContext validationContext() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tiktok.unique-id", "creator");
        PluginConfig config = PluginConfig.load(yaml).config();
        return new ActionContext(null, config, null, null, null, null, null);
    }

    private static TikTokEventContext chat(String text) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("chat.text", text);
        return new TikTokEventContext(
                "id-1", "room-1", TikTokEventContext.CHAT, "1", "viewer", "Viewer", 1L, false,
                payload);
    }

    @Test void aScriptRuleMatchesItsEvent() throws Exception {
        List<EventRule> rules =
                new DslCompiler("example.ttl", new ActionRegistry(), validationContext())
                        .compile(
                                "on chat where chat.text == \"!ttl\" {\n"
                                        + "    cooldown 10s per user\n"
                                        + "    broadcast \"Hello from the script!\"\n"
                                        + "}\n");
        EventRule rule = rules.get(0);
        assertNotNull(rule.condition());
        assertTrue(rule.matches(chat("!ttl")), "the rule should match");
        assertTrue(!rule.matches(chat("other")), "the rule should not match another text");
    }
}
