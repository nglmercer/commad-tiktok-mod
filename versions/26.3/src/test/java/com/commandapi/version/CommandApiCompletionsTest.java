package com.commandapi.version;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandApiCompletionsTest {

    private static List<String> suggestions(CommandDispatcher<Object> dispatcher, String text) throws Exception {
        ParseResults<Object> parsed = dispatcher.parse(text, new Object());
        return dispatcher.getCompletionSuggestions(parsed).get().getList().stream()
                .map(Suggestion::getText).collect(Collectors.toList());
    }

    @Test
    void completesRootSubcommandsAndValues() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        CommandApiCompletions.register(dispatcher);
        assertTrue(suggestions(dispatcher, "commanda").contains("commandapi"));
        assertTrue(suggestions(dispatcher, "commandapi ").contains("restart"));
        assertTrue(suggestions(dispatcher, "commandapi ").contains("status"));
        assertTrue(suggestions(dispatcher, "commandapi auth ").contains("on"));
        assertTrue(suggestions(dispatcher, "commandapi port ").contains("0"));
    }
}
