package dev.nglmercer.tiktokminecraft.dsl;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionException;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.rules.ActionSpec;
import dev.nglmercer.tiktokminecraft.rules.Aggregate;
import dev.nglmercer.tiktokminecraft.rules.Comparison;
import dev.nglmercer.tiktokminecraft.rules.Condition;
import dev.nglmercer.tiktokminecraft.rules.CooldownSpec;
import dev.nglmercer.tiktokminecraft.rules.Durations;
import dev.nglmercer.tiktokminecraft.rules.EventFields;
import dev.nglmercer.tiktokminecraft.rules.EventRule;
import dev.nglmercer.tiktokminecraft.rules.FieldMatcher;
import dev.nglmercer.tiktokminecraft.rules.RulesSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compiles a script into the same rules a YAML file produces.
 *
 * <p>There is only one rule engine: a script and a YAML file end up as the
 * same {@link EventRule} objects, with the same limits, the same diagnostics
 * and the same behaviour.</p>
 */
public final class DslCompiler {

    private final String file;
    private final ActionRegistry registry;
    private final ActionContext validationContext;

    public DslCompiler(
            String file, ActionRegistry registry, ActionContext validationContext) {
        this.file = file;
        this.registry = registry;
        this.validationContext = validationContext;
    }

    /** Compiles a script. */
    public List<EventRule> compile(String source) throws DslException {
        List<Ast.Rule> parsed = Parser.parse(source, file);
        List<EventRule> rules = new ArrayList<EventRule>();
        String stem = stem();
        int index = 0;
        for (Ast.Rule rule : parsed) {
            index++;
            rules.add(compile(rule, stem + "." + index));
        }
        return rules;
    }

    private EventRule compile(Ast.Rule rule, String id) throws DslException {
        String trigger = rule.trigger().toLowerCase(Locale.ROOT);
        if (!RulesSnapshot.isKnownTrigger(trigger)) {
            throw new DslException(
                    file, 1, 1, "unknown trigger '" + rule.trigger() + "'", null);
        }
        List<FieldMatcher> matchers = new ArrayList<FieldMatcher>();
        Condition condition = null;
        if (rule.condition() != null) {
            condition = buildCondition(rule.condition());
        }
        Aggregate aggregate = null;
        if (rule.threshold() != null) {
            Aggregate.Scope scope = Aggregate.Scope.parse(rule.threshold().scope());
            if (scope == null) {
                throw new DslException(file, 1, 1,
                        "unknown scope '" + rule.threshold().scope() + "'", null);
            }
            if (rule.threshold().value() <= 0) {
                throw new DslException(file, 1, 1, "the threshold must be positive", null);
            }
            if (!"like".equals(trigger)) {
                throw new DslException(file, 1, 1,
                        "'every' can only be used with the 'like' trigger", null);
            }
            aggregate =
                    new Aggregate(
                            Aggregate.Metric.COUNT,
                            scope,
                            rule.threshold().value(),
                            true);
        }
        CooldownSpec cooldown = null;
        List<ActionSpec> actions = new ArrayList<ActionSpec>();
        for (Ast.Statement statement : rule.statements()) {
            switch (statement.kind()) {
                case COOLDOWN: {
                    long duration = Durations.parseMillis(statement.text());
                    if (duration < 0) {
                        throw new DslException(file, 1, 1,
                                "'" + statement.text() + "' is not a valid duration", null);
                    }
                    CooldownSpec.Scope scope = CooldownSpec.Scope.parse(statement.scope());
                    if (scope == null) {
                        throw new DslException(file, 1, 1,
                                "unknown cooldown scope '" + statement.scope() + "'", null);
                    }
                    cooldown = new CooldownSpec(duration, scope);
                    break;
                }
                case CALL: {
                    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
                    parameters.put("name", statement.text());
                    if (!statement.arguments().isEmpty()) {
                        parameters.put("args", new LinkedHashMap<String, Object>(
                                statement.arguments()));
                    }
                    actions.add(new ActionSpec("function", parameters));
                    break;
                }
                case BROADCAST: {
                    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
                    parameters.put("message", statement.text());
                    actions.add(new ActionSpec("broadcast", parameters));
                    break;
                }
                default:
                    throw new DslException(file, 1, 1, "unknown statement", null);
            }
        }
        if (actions.isEmpty()) {
            throw new DslException(file, 1, 1, "the rule has no actions", null);
        }
        for (ActionSpec spec : actions) {
            validate(spec);
        }
        return new EventRule(
                id,
                true,
                trigger,
                matchers,
                aggregate,
                cooldown,
                condition,
                actions,
                0,
                false,
                file);
    }

    /**
     * Builds the condition tree.
     *
     * <p>Comparisons joined by {@code and} form a group; the groups are
     * joined by {@code or}.</p>
     */
    private Condition buildCondition(Ast.Condition source) throws DslException {
        List<List<Ast.Comparison>> groups = new ArrayList<List<Ast.Comparison>>();
        List<Ast.Comparison> current = new ArrayList<Ast.Comparison>();
        for (int index = 0; index < source.comparisons().size(); index++) {
            Ast.Comparison comparison = source.comparisons().get(index);
            if (index > 0) {
                String connector = source.connectors().get(index - 1);
                if (connector.equals("or")) {
                    groups.add(current);
                    current = new ArrayList<Ast.Comparison>();
                }
            }
            current.add(comparison);
        }
        groups.add(current);

        List<Condition> alternatives = new ArrayList<Condition>();
        for (List<Ast.Comparison> group : groups) {
            List<Condition> leaves = new ArrayList<Condition>();
            for (Ast.Comparison comparison : group) {
                if (!EventFields.isKnown(comparison.field())) {
                    throw new DslException(file, 1, 1,
                            "unknown field '" + comparison.field() + "'", null);
                }
                Comparison.Operator operator = Comparison.parse(comparison.operator());
                if (operator == null) {
                    throw new DslException(file, 1, 1,
                            "unsupported comparison '" + comparison.operator() + "'", null);
                }
                leaves.add(
                        Condition.leaf(
                                new FieldMatcher(
                                        comparison.field(), operator, comparison.value())));
            }
            alternatives.add(Condition.all(leaves));
        }
        if (alternatives.size() == 1) {
            return alternatives.get(0);
        }
        return Condition.any(alternatives);
    }

    private void validate(ActionSpec spec) throws DslException {
        dev.nglmercer.tiktokminecraft.actions.Action action = registry.get(spec.type());
        if (action == null) {
            throw new DslException(file, 1, 1,
                    "unknown action type '" + spec.type() + "'", null);
        }
        try {
            action.validate(spec, validationContext);
        } catch (ActionException failure) {
            throw new DslException(file, 1, 1, failure.getMessage(), null);
        } catch (RuntimeException failure) {
            throw new DslException(file, 1, 1, String.valueOf(failure.getMessage()), null);
        }
    }

    /** A rule id prefix built from the file name. */
    private String stem() {
        String name = file == null ? "script" : file;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".ttl")) {
            name = name.substring(0, name.length() - 4);
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_') {
                builder.append(character);
            } else {
                builder.append('.');
            }
        }
        String result = builder.toString();
        return result.isEmpty() ? "script" : result;
    }

    /** An empty rule list. */
    public static List<EventRule> none() {
        return Collections.emptyList();
    }
}
