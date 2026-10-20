package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;

/** One typed comparison between an event field and a literal value. */
public final class FieldMatcher {

    private final String field;
    private final Comparison.Operator operator;
    private final Object value;

    public FieldMatcher(String field, Comparison.Operator operator, Object value) {
        // The field is resolved to its canonical path once, so a diagnostic
        // always names the field of the normalized schema.
        this.field = EventFields.resolve(field);
        this.operator = operator;
        this.value = value;
    }

    public String field() {
        return field;
    }

    public Comparison.Operator operator() {
        return operator;
    }

    public Object value() {
        return value;
    }

    /** @return true when the event satisfies this comparison */
    public boolean matches(TikTokEventContext event) {
        Object actual = EventFields.read(event, field);
        if (actual == null) {
            return false;
        }
        return Comparison.test(operator, actual, value);
    }

    @Override
    public String toString() {
        return field + " " + operator.wireName() + " " + value;
    }
}
