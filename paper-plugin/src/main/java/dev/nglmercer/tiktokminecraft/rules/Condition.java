package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import java.util.Collections;
import java.util.List;

/**
 * A boolean tree over field comparisons.
 *
 * <p>A condition is a combination of {@code all}, {@code any} and {@code not}
 * groups. It never evaluates an expression.</p>
 */
public final class Condition {

    /** The kind of a node. */
    public enum Kind {
        /** Every child must hold. */
        ALL,
        /** At least one child must hold. */
        ANY,
        /** The child must not hold. */
        NOT,
        /** A single comparison. */
        LEAF
    }

    private final Kind kind;
    private final List<Condition> children;
    private final FieldMatcher leaf;

    private Condition(Kind kind, List<Condition> children, FieldMatcher leaf) {
        this.kind = kind;
        this.children = Collections.unmodifiableList(children);
        this.leaf = leaf;
    }

    public static Condition all(List<Condition> children) {
        return new Condition(Kind.ALL, children, null);
    }

    public static Condition any(List<Condition> children) {
        return new Condition(Kind.ANY, children, null);
    }

    public static Condition not(Condition child) {
        return new Condition(Kind.NOT, Collections.singletonList(child), null);
    }

    public static Condition leaf(FieldMatcher matcher) {
        return new Condition(Kind.LEAF, Collections.<Condition>emptyList(), matcher);
    }

    public Kind kind() {
        return kind;
    }

    public List<Condition> children() {
        return children;
    }

    public FieldMatcher leaf() {
        return leaf;
    }

    /** @return true when the event satisfies this condition */
    public boolean test(TikTokEventContext event) {
        switch (kind) {
            case ALL:
                for (Condition child : children) {
                    if (!child.test(event)) {
                        return false;
                    }
                }
                return true;
            case ANY:
                for (Condition child : children) {
                    if (child.test(event)) {
                        return true;
                    }
                }
                return false;
            case NOT:
                return !children.get(0).test(event);
            case LEAF:
            default:
                return leaf.matches(event);
        }
    }
}
