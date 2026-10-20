package com.commandapi.version;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;

/** Adds local-only syntax to each server command tree for chat completion. */
public final class CommandApiCompletions {

    private CommandApiCompletions() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("commandapi")
                .executes(context -> 1);
        for (String name : new String[]{"help", "status", "reload", "restart"}) {
            root.then(LiteralArgumentBuilder.<S>literal(name).executes(context -> 1));
        }
        root.then(value("port", "0"));
        root.then(value("host", "127.0.0.1"));
        root.then(value("auth", "on", "off"));
        root.then(value("token", "clear"));
        root.then(value("login", "on", "off"));
        dispatcher.register(root);
    }

    private static <S> LiteralArgumentBuilder<S> value(String name, String... suggestions) {
        SuggestionProvider<S> provider = (context, builder) -> {
            for (String suggestion : suggestions) {
                if (suggestion.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                    builder.suggest(suggestion);
                }
            }
            return builder.buildFuture();
        };
        RequiredArgumentBuilder<S, String> argument = RequiredArgumentBuilder
                .<S, String>argument("value", StringArgumentType.greedyString())
                .suggests(provider).executes(context -> 1);
        return LiteralArgumentBuilder.<S>literal(name)
                .executes(context -> 1)
                .then(argument);
    }
}
