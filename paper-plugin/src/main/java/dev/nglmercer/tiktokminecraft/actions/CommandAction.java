package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.rules.ActionSpec;

/**
 * Runs a raw server command.
 *
 * <p>This is the most dangerous action and it is disabled by default. It
 * needs both {@code security.allow-raw-commands} and a command root in the
 * allowlist. The command text must not contain anything a viewer could
 * influence, because a raw command is the one place where a mistake becomes
 * a server compromise.</p>
 */
public final class CommandAction extends Action {

    @Override
    public String type() {
        return "command";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String command = require(spec, "command");
        if (command.length() > 256) {
            throw new ActionException("'command' must be at most 256 characters");
        }
        if (command.contains("\n") || command.contains("\r") || command.contains(";")) {
            throw new ActionException("'command' must not contain line breaks or ';'");
        }
        if (command.startsWith("/")) {
            throw new ActionException("'command' must not start with '/'");
        }
        if (!context.config().allowRawCommands()) {
            throw new ActionException(
                    "Raw commands are disabled (security.allow-raw-commands)");
        }
        if (!context.config().allowConsoleCommands()) {
            throw new ActionException(
                    "Console commands are disabled (security.allow-console-commands)");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        if (!context.config().allowRawCommands() || !context.config().allowConsoleCommands()) {
            return ActionResult.skipped("Raw commands are disabled");
        }
        String command = spec.string("command", "");
        int space = command.indexOf(' ');
        String root = space < 0 ? command : command.substring(0, space);
        if (!context.config().allowedCommands().contains(root.toLowerCase(java.util.Locale.ROOT))) {
            return ActionResult.skipped("The command root is not in the allowlist");
        }
        context.plugin()
                .getServer()
                .dispatchCommand(
                        context.plugin().getServer().getConsoleSender(), command);
        return ActionResult.ok("Dispatched /" + command);
    }
}
