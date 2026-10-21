package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionException;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads the event rules from {@code events/}.
 *
 * <p>Every rule is validated while it is loaded: an unknown trigger, an
 * unknown action, a missing function, a malformed cooldown, an unsupported
 * comparison or an unknown field is an error that names the file, the rule
 * and the configuration path.</p>
 *
 * <p>A file that fails does not take the whole set down: the valid rules
 * from the other files are still loaded, and the failures are reported.</p>
 */
public final class YamlRuleLoader {

    /** The outcome of a load. */
    public static final class LoadResult {
        private final RulesSnapshot snapshot;
        private final Map<String, String> failures;

        LoadResult(RulesSnapshot snapshot, Map<String, String> failures) {
            this.snapshot = snapshot;
            this.failures = Collections.unmodifiableMap(failures);
        }

        public RulesSnapshot snapshot() {
            return snapshot;
        }

        /** The problems, keyed by the file they were found in. */
        public Map<String, String> failures() {
            return failures;
        }

        public boolean isValid() {
            return failures.isEmpty();
        }
    }

    private final ActionRegistry registry;
    private final ActionContext validationContext;

    public YamlRuleLoader(ActionRegistry registry, ActionContext validationContext) {
        this.registry = registry;
        this.validationContext = validationContext;
    }

    /** Loads every rule file in a directory. */
    public LoadResult load(Path eventsDirectory) {
        List<EventRule> rules = new ArrayList<EventRule>();
        Map<String, String> failures = new LinkedHashMap<String, String>();
        if (eventsDirectory == null || !Files.isDirectory(eventsDirectory)) {
            return new LoadResult(new RulesSnapshot(rules), failures);
        }
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(eventsDirectory, "*.yml")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException failure) {
            failures.put(eventsDirectory.toString(), "The directory could not be read");
            return new LoadResult(new RulesSnapshot(rules), failures);
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                rules.addAll(read(file, name));
            } catch (RuleException failure) {
                failures.put(name, failure.getMessage());
            } catch (IOException failure) {
                failures.put(name, "The file could not be read: " + failure.getMessage());
            }
        }
        return new LoadResult(new RulesSnapshot(rules), failures);
    }

    private List<EventRule> read(Path file, String name) throws IOException, RuleException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException failure) {
            throw new RuleException("The file is not valid YAML: " + failure.getMessage());
        }
        List<EventRule> rules = new ArrayList<EventRule>();
        if (!yaml.contains("rules")) {
            // A file without a rules section simply contributes nothing.
            return rules;
        }
        List<?> entries = yaml.getList("rules");
        if (entries == null) {
            throw new RuleException("'rules' must be a list");
        }
        int index = 0;
        for (Object entry : entries) {
            String path = "rules[" + index + "]";
            index++;
            if (!(entry instanceof Map)) {
                throw new RuleException(path + " must be a mapping");
            }
            @SuppressWarnings("unchecked")
            Map<?, ?> map = (Map<?, ?>) entry;
            rules.add(readRule(map, path, name));
        }
        return rules;
    }

    private EventRule readRule(Map<?, ?> map, String path, String file) throws RuleException {
        String id = text(map.get("id"));
        if (id.isEmpty()) {
            throw new RuleException(path + ".id is required");
        }
        if (!id.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new RuleException(path + ".id must be at most 64 letters, digits, '.' or '_'");
        }
        String where = file + " " + path + " (rule '" + id + "')";

        boolean enabled = bool(map.get("enabled"), true);
        String trigger = text(map.get("trigger")).toLowerCase(Locale.ROOT);
        if (!RulesSnapshot.isKnownTrigger(trigger)) {
            throw new RuleException(where + ": unknown trigger '" + trigger + "'");
        }

        List<FieldMatcher> matchers = new ArrayList<FieldMatcher>();
        Object match = map.get("match");
        if (match instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) match).entrySet()) {
                String field = String.valueOf(entry.getKey());
                matchers.addAll(readMatcher(field, entry.getValue(), where + ".match." + field));
            }
        } else if (match != null) {
            throw new RuleException(where + ".match must be a mapping");
        }

        Condition condition = null;
        Object conditions = map.get("conditions");
        if (conditions instanceof List) {
            condition = readConditions((List<?>) conditions, where + ".conditions");
        } else if (conditions != null) {
            throw new RuleException(where + ".conditions must be a list");
        }

        Aggregate aggregate = null;
        Object aggregateSection = map.get("aggregate");
        if (aggregateSection instanceof Map) {
            aggregate = readAggregate((Map<?, ?>) aggregateSection, where + ".aggregate", trigger);
        } else if (aggregateSection != null) {
            throw new RuleException(where + ".aggregate must be a mapping");
        }

        CooldownSpec cooldown = null;
        Object cooldownSection = map.get("cooldown");
        if (cooldownSection instanceof Map) {
            cooldown = readCooldown((Map<?, ?>) cooldownSection, where + ".cooldown");
        } else if (cooldownSection != null) {
            throw new RuleException(where + ".cooldown must be a mapping");
        }

        List<ActionSpec> actions = readActions(map.get("actions"), where + ".actions");

        int priority = 0;
        Object priorityValue = map.get("priority");
        if (priorityValue != null) {
            try {
                priority = priorityValue instanceof Number
                        ? ((Number) priorityValue).intValue()
                        : Integer.parseInt(text(priorityValue));
            } catch (NumberFormatException failure) {
                throw new RuleException(where + ".priority must be a whole number");
            }
            if (priority < -1000 || priority > 1000) {
                throw new RuleException(where + ".priority must be between -1000 and 1000");
            }
        }
        boolean stopProcessing = bool(map.get("stop-processing"), false);
        return new EventRule(
                id,
                enabled,
                trigger,
                matchers,
                aggregate,
                cooldown,
                condition,
                actions,
                priority,
                stopProcessing,
                file);
    }

    private List<FieldMatcher> readMatcher(String field, Object value, String where)
            throws RuleException {
        if (!EventFields.isKnown(field)) {
            throw new RuleException(where + ": unknown field '" + field + "'");
        }
        List<FieldMatcher> matchers = new ArrayList<FieldMatcher>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String operatorName = String.valueOf(entry.getKey());
                Comparison.Operator operator = Comparison.parse(operatorName);
                if (operator == null) {
                    throw new RuleException(
                            where + ": unsupported comparison '" + operatorName + "'");
                }
                matchers.add(new FieldMatcher(field, operator, entry.getValue()));
            }
            if (matchers.isEmpty()) {
                throw new RuleException(where + " must name a comparison");
            }
            return matchers;
        }
        // A plain value is shorthand for an equality test.
        matchers.add(new FieldMatcher(field, Comparison.Operator.EQUALS, value));
        return matchers;
    }

    private Condition readConditions(List<?> entries, String where) throws RuleException {
        List<Condition> children = new ArrayList<Condition>();
        for (Object entry : entries) {
            children.add(readCondition(entry, where));
        }
        if (children.isEmpty()) {
            throw new RuleException(where + " must not be empty");
        }
        return Condition.all(children);
    }

    private Condition readCondition(Object entry, String where) throws RuleException {
        if (!(entry instanceof Map)) {
            throw new RuleException(where + " must be a mapping");
        }
        Map<?, ?> map = (Map<?, ?>) entry;
        if (map.containsKey("all")) {
            return Condition.all(readConditionList(map.get("all"), where + ".all"));
        }
        if (map.containsKey("any")) {
            return Condition.any(readConditionList(map.get("any"), where + ".any"));
        }
        if (map.containsKey("not")) {
            Object inner = map.get("not");
            if (inner instanceof Map && ((Map<?, ?>) inner).containsKey("all")) {
                return Condition.not(
                        Condition.all(
                                readConditionList(
                                        ((Map<?, ?>) inner).get("all"), where + ".not.all")));
            }
            if (inner instanceof Map && ((Map<?, ?>) inner).containsKey("any")) {
                return Condition.not(
                        Condition.any(
                                readConditionList(
                                        ((Map<?, ?>) inner).get("any"), where + ".not.any")));
            }
            return Condition.not(readCondition(inner, where + ".not"));
        }
        String field = text(map.get("field"));
        if (field.isEmpty()) {
            throw new RuleException(where + ".field is required");
        }
        if (!EventFields.isKnown(field)) {
            throw new RuleException(where + ": unknown field '" + field + "'");
        }
        Comparison.Operator operator =
                Comparison.parse(text(map.get("operator")).isEmpty()
                        ? "equals"
                        : text(map.get("operator")));
        if (operator == null) {
            throw new RuleException(where + ": unsupported comparison");
        }
        if (!map.containsKey("value")) {
            throw new RuleException(where + ".value is required");
        }
        return Condition.leaf(new FieldMatcher(field, operator, map.get("value")));
    }

    private List<Condition> readConditionList(Object value, String where) throws RuleException {
        if (!(value instanceof List)) {
            throw new RuleException(where + " must be a list");
        }
        List<Condition> children = new ArrayList<Condition>();
        int index = 0;
        for (Object entry : (List<?>) value) {
            children.add(readCondition(entry, where + "[" + index + "]"));
            index++;
        }
        if (children.isEmpty()) {
            throw new RuleException(where + " must not be empty");
        }
        return children;
    }

    private Aggregate readAggregate(Map<?, ?> map, String where, String trigger)
            throws RuleException {
        Aggregate.Metric metric = Aggregate.Metric.parse(text(map.get("metric")));
        if (metric == null) {
            throw new RuleException(where + ".metric must be 'count' or 'total'");
        }
        Aggregate.Scope scope = Aggregate.Scope.parse(text(map.get("scope")));
        if (scope == null) {
            throw new RuleException(where + ".scope must be 'event', 'user' or 'global'");
        }
        Object thresholdValue = map.get("threshold");
        if (thresholdValue == null) {
            throw new RuleException(where + ".threshold is required");
        }
        long threshold;
        try {
            threshold = thresholdValue instanceof Number
                    ? ((Number) thresholdValue).longValue()
                    : Long.parseLong(text(thresholdValue));
        } catch (NumberFormatException failure) {
            throw new RuleException(where + ".threshold must be a whole number");
        }
        if (threshold <= 0 || threshold > 1_000_000L) {
            throw new RuleException(where + ".threshold must be between 1 and 1000000");
        }
        if (!"like".equals(trigger)) {
            throw new RuleException(
                    where + " can only be used with the 'like' trigger, not '" + trigger + "'");
        }
        boolean carryRemainder = bool(map.get("carry-remainder"), false);
        return new Aggregate(metric, scope, threshold, carryRemainder);
    }

    private CooldownSpec readCooldown(Map<?, ?> map, String where) throws RuleException {
        long duration = Durations.parseMillis(text(map.get("duration")));
        if (duration < 0) {
            throw new RuleException(
                    where + ".duration must be a duration such as '10s', '2m' or '500ms'");
        }
        if (duration > 86_400_000L) {
            throw new RuleException(where + ".duration must be at most 24 hours");
        }
        CooldownSpec.Scope scope =
                CooldownSpec.Scope.parse(
                        text(map.get("scope")).isEmpty() ? "global" : text(map.get("scope")));
        if (scope == null) {
            throw new RuleException(
                    where + ".scope must be 'global', 'user', 'rule' or 'user-and-rule'");
        }
        return new CooldownSpec(duration, scope);
    }

    private List<ActionSpec> readActions(Object value, String where) throws RuleException {
        List<ActionSpec> actions = new ArrayList<ActionSpec>();
        if (value == null) {
            return actions;
        }
        if (!(value instanceof List)) {
            throw new RuleException(where + " must be a list");
        }
        List<?> entries = (List<?>) value;
        int index = 0;
        for (Object entry : entries) {
            String path = where + "[" + index + "]";
            index++;
            if (!(entry instanceof Map)) {
                throw new RuleException(path + " must be a mapping");
            }
            Map<?, ?> map = (Map<?, ?>) entry;
            String type = text(map.get("type"));
            if (type.isEmpty()) {
                throw new RuleException(path + ".type is required");
            }
            if (!registry.contains(type)) {
                throw new RuleException(
                        path + ": unknown action type '" + type + "'; known types are "
                                + registry.typeList());
            }
            Map<String, Object> parameters = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> parameter : map.entrySet()) {
                String name = String.valueOf(parameter.getKey());
                if (name.equals("type")) {
                    continue;
                }
                parameters.put(name, parameter.getValue());
            }
            ActionSpec spec = new ActionSpec(type, parameters);
            validateAction(spec, path);
            actions.add(spec);
        }
        if (actions.isEmpty()) {
            throw new RuleException(where + " must not be empty");
        }
        return actions;
    }

    private void validateAction(ActionSpec spec, String path) throws RuleException {
        try {
            dev.nglmercer.tiktokminecraft.actions.Action action = registry.get(spec.type());
            action.validate(spec, validationContext);
        } catch (ActionException failure) {
            throw new RuleException(path + ": " + failure.getMessage());
        } catch (RuntimeException failure) {
            throw new RuleException(path + ": " + failure.getMessage());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes")) {
            return true;
        }
        if (text.equals("false") || text.equals("no")) {
            return false;
        }
        return fallback;
    }

    /** A rule that could not be read. */
    public static final class RuleException extends Exception {
        private static final long serialVersionUID = 1L;

        RuleException(String message) {
            super(message);
        }
    }
}
