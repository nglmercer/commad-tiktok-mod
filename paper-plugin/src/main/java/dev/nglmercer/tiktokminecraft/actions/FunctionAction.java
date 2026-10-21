package dev.nglmercer.tiktokminecraft.actions;

import dev.nglmercer.tiktokminecraft.functions.ExecutionContext;
import dev.nglmercer.tiktokminecraft.functions.FunctionEngineService;
import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Runs a reusable function. */
public final class FunctionAction extends Action {

    @Override
    public String type() {
        return "function";
    }

    @Override
    public void validate(ActionSpec spec, ActionContext context) throws ActionException {
        String name = require(spec, "name");
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new ActionException("'" + name + "' is not a valid function id");
        }
        FunctionEngineService functions = context.functions();
        if (functions == null) {
            // Without the engine the reference cannot be checked here; the
            // call is still rejected when it runs.
            return;
        }
        if (functions.definition(name) == null) {
            throw new ActionException("Unknown function: " + name);
        }
        Object args = spec.parameter("args");
        if (args != null && !(args instanceof Map)) {
            throw new ActionException("'args' must be a mapping of argument names to values");
        }
    }

    @Override
    public ActionResult execute(ActionSpec spec, ActionContext context)
            throws ActionException {
        String name = spec.string("name", "");
        FunctionEngineService functions = context.functions();
        if (functions == null) {
            return ActionResult.failed("Functions are not available");
        }
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        Object raw = spec.parameter("args");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                args.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        UUID target = context.primaryTarget();
        ExecutionContext execution =
                new ExecutionContext(
                        target, context.ruleId(), "rule " + context.ruleId());
        functions.execute(name, args, execution);
        return ActionResult.ok("Started function " + name);
    }
}
