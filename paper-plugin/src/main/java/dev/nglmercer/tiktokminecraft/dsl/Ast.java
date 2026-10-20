package dev.nglmercer.tiktokminecraft.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The syntax tree of a script.
 *
 * <p>The tree is deliberately small: a trigger, an optional condition, an
 * optional threshold and a list of statements. It holds no behaviour, so a
 * script can never do anything on its own.</p>
 */
public final class Ast {

    private Ast() {
    }

    /** One rule. */
    public static final class Rule {
        private final String trigger;
        private final Condition condition;
        private final Threshold threshold;
        private final List<Statement> statements;

        public Rule(
                String trigger,
                Condition condition,
                Threshold threshold,
                List<Statement> statements) {
            this.trigger = trigger;
            this.condition = condition;
            this.threshold = threshold;
            this.statements = Collections.unmodifiableList(
                    new ArrayList<Statement>(statements));
        }

        public String trigger() {
            return trigger;
        }

        public Condition condition() {
            return condition;
        }

        public Threshold threshold() {
            return threshold;
        }

        public List<Statement> statements() {
            return statements;
        }
    }

    /** A comparison between a field and a value. */
    public static final class Comparison {
        private final String field;
        private final String operator;
        private final Object value;

        public Comparison(String field, String operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }

        public String field() {
            return field;
        }

        public String operator() {
            return operator;
        }

        public Object value() {
            return value;
        }
    }

    /** A list of comparisons joined by {@code and} or {@code or}. */
    public static final class Condition {
        private final List<Comparison> comparisons;
        private final List<String> connectors;

        public Condition(List<Comparison> comparisons, List<String> connectors) {
            this.comparisons = Collections.unmodifiableList(
                    new ArrayList<Comparison>(comparisons));
            this.connectors = Collections.unmodifiableList(new ArrayList<String>(connectors));
        }

        public List<Comparison> comparisons() {
            return comparisons;
        }

        /** The connector before each comparison after the first. */
        public List<String> connectors() {
            return connectors;
        }
    }

    /** A threshold over a counter. */
    public static final class Threshold {
        private final long value;
        private final String scope;

        public Threshold(long value, String scope) {
            this.value = value;
            this.scope = scope;
        }

        public long value() {
            return value;
        }

        public String scope() {
            return scope;
        }
    }

    /** One statement inside a rule. */
    public static final class Statement {
        /** What a statement does. */
        public enum Kind {
            COOLDOWN,
            CALL,
            BROADCAST
        }

        private final Kind kind;
        private final String text;
        private final String scope;
        private final Map<String, Object> arguments;

        public Statement(
                Kind kind, String text, String scope, Map<String, Object> arguments) {
            this.kind = kind;
            this.text = text;
            this.scope = scope;
            this.arguments = Collections.unmodifiableMap(
                    arguments == null
                            ? new LinkedHashMap<String, Object>()
                            : new LinkedHashMap<String, Object>(arguments));
        }

        public Kind kind() {
            return kind;
        }

        /** The duration, the function id or the message. */
        public String text() {
            return text;
        }

        /** The scope of a cooldown, or null. */
        public String scope() {
            return scope;
        }

        /** The arguments of a call. */
        public Map<String, Object> arguments() {
            return arguments;
        }
    }
}
