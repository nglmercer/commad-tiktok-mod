package dev.nglmercer.tiktokminecraft.functions;

import java.util.Collections;
import java.util.List;

/**
 * The commands a function would run, resolved but not executed.
 */
public final class FunctionPreview {

    private final String functionId;
    private final List<String> commands;
    private final List<String> steps;

    public FunctionPreview(String functionId, List<String> commands, List<String> steps) {
        this.functionId = functionId;
        this.commands = Collections.unmodifiableList(commands);
        this.steps = Collections.unmodifiableList(steps);
    }

    public String functionId() {
        return functionId;
    }

    /** The commands that would be dispatched, in order. */
    public List<String> commands() {
        return commands;
    }

    /** A readable description of every step, including delays. */
    public List<String> steps() {
        return steps;
    }
}
