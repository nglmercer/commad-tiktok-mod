package dev.nglmercer.tiktokminecraft.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a script into an {@link Ast}.
 *
 * <p>The grammar is small and closed. There is no expression, no function
 * call of the host language, no import and no way to reach anything but the
 * listed statements.</p>
 */
public final class Parser {

    private final List<Token> tokens;
    private final String file;
    private int index;

    private Parser(List<Token> tokens, String file) {
        this.tokens = tokens;
        this.file = file;
    }

    /** Parses a whole script. */
    public static List<Ast.Rule> parse(String source, String file) throws DslException {
        Parser parser = new Parser(Lexer.tokenize(source, file), file);
        return parser.parseScript();
    }

    private List<Ast.Rule> parseScript() throws DslException {
        List<Ast.Rule> rules = new ArrayList<Ast.Rule>();
        while (!peek().is(TokenType.END)) {
            rules.add(parseRule());
        }
        return rules;
    }

    private Ast.Rule parseRule() throws DslException {
        Token on = expectKeyword("on");
        Token trigger = expect(TokenType.IDENTIFIER, "a trigger name");
        Ast.Condition condition = null;
        if (peek().isKeyword("where")) {
            next();
            condition = parseCondition();
        }
        Ast.Threshold threshold = null;
        if (peek().isKeyword("every")) {
            next();
            threshold = parseThreshold();
        }
        expect(TokenType.LEFT_BRACE, "'{'");
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
        while (!peek().is(TokenType.RIGHT_BRACE)) {
            if (peek().is(TokenType.END)) {
                throw error(peek(), "The rule is not closed", "'}'");
            }
            statements.add(parseStatement());
        }
        expect(TokenType.RIGHT_BRACE, "'}'");
        if (statements.isEmpty()) {
            throw error(on, "The rule has no statements", "at least one statement");
        }
        return new Ast.Rule(trigger.text(), condition, threshold, statements);
    }

    private Ast.Condition parseCondition() throws DslException {
        List<Ast.Comparison> comparisons = new ArrayList<Ast.Comparison>();
        List<String> connectors = new ArrayList<String>();
        comparisons.add(parseComparison());
        while (peek().isKeyword("and") || peek().isKeyword("or")) {
            connectors.add(next().text());
            comparisons.add(parseComparison());
        }
        return new Ast.Condition(comparisons, connectors);
    }

    private Ast.Comparison parseComparison() throws DslException {
        Token field = expect(TokenType.IDENTIFIER, "a field name");
        Token operator = next();
        TokenType expected = operator.type();
        if (expected != TokenType.EQUAL && expected != TokenType.NOT_EQUAL
                && expected != TokenType.GREATER && expected != TokenType.GREATER_OR_EQUAL
                && expected != TokenType.LESS && expected != TokenType.LESS_OR_EQUAL) {
            throw error(operator, "Unsupported comparison '" + operator.text() + "'",
                    "'==', '!=', '>', '>=', '<' or '<='");
        }
        Token value = next();
        if (!value.is(TokenType.STRING) && !value.is(TokenType.NUMBER)
                && !value.is(TokenType.IDENTIFIER)) {
            throw error(value, "A comparison needs a value", "a string, a number or a word");
        }
        Object parsed = value.is(TokenType.NUMBER)
                ? Long.valueOf(value.text())
                : value.text();
        return new Ast.Comparison(field.text(), operator.text(), parsed);
    }

    private Ast.Threshold parseThreshold() throws DslException {
        Token count = expect(TokenType.NUMBER, "a number");
        String scope = "global";
        if (peek().isKeyword("per")) {
            next();
            Token word = expect(TokenType.IDENTIFIER, "a scope");
            scope = "user";
            if (peek().isKeyword("and")) {
                next();
                Token second = expect(TokenType.IDENTIFIER, "a scope");
                if (!second.text().equals("rule")) {
                    throw error(second, "Only 'rule' can follow 'and'", "'rule'");
                }
                scope = "user-and-rule";
            }
        } else if (peek().is(TokenType.IDENTIFIER)
                && (peek().text().equals("global") || peek().text().equals("user"))) {
            scope = next().text();
        }
        long value;
        try {
            value = Long.parseLong(count.text());
        } catch (NumberFormatException failure) {
            throw error(count, "The threshold is not a whole number", "a whole number");
        }
        return new Ast.Threshold(value, scope);
    }

    private Ast.Statement parseStatement() throws DslException {
        Token keyword = expect(TokenType.IDENTIFIER, "a statement");
        if (keyword.text().equals("cooldown")) {
            Token duration = expect(TokenType.DURATION, "a duration such as '10s'");
            String scope = "global";
            if (peek().isKeyword("per")) {
                next();
                Token word = expect(TokenType.IDENTIFIER, "a scope");
                scope = word.text();
                if (peek().isKeyword("and")) {
                    next();
                    Token second = expect(TokenType.IDENTIFIER, "a scope");
                    if (!second.text().equals("rule")) {
                        throw error(second, "Only 'rule' can follow 'and'", "'rule'");
                    }
                    scope = "user-and-rule";
                }
            } else if (peek().is(TokenType.IDENTIFIER) && peek().text().equals("global")) {
                next();
            }
            return new Ast.Statement(Ast.Statement.Kind.COOLDOWN, duration.text(), scope, null);
        }
        if (keyword.text().equals("broadcast")) {
            Token message = expect(TokenType.STRING, "a message in quotes");
            return new Ast.Statement(Ast.Statement.Kind.BROADCAST, message.text(), null, null);
        }
        if (keyword.text().equals("call")) {
            Token function = expect(TokenType.IDENTIFIER, "a function id");
            java.util.Map<String, Object> arguments = new java.util.LinkedHashMap<String, Object>();
            if (peek().is(TokenType.LEFT_PAREN)) {
                next();
                if (!peek().is(TokenType.RIGHT_PAREN)) {
                    while (true) {
                        Token name = expect(TokenType.IDENTIFIER, "an argument name");
                        expect(TokenType.ASSIGN, "'='");
                        Token value = next();
                        if (!value.is(TokenType.STRING) && !value.is(TokenType.NUMBER)
                                && !value.is(TokenType.IDENTIFIER)) {
                            throw error(value, "An argument needs a value",
                                    "a string, a number or a word");
                        }
                        arguments.put(
                                name.text(),
                                value.is(TokenType.NUMBER) ? (Object) Long.valueOf(value.text())
                                        : (Object) value.text());
                        if (peek().is(TokenType.COMMA)) {
                            next();
                            continue;
                        }
                        break;
                    }
                }
                expect(TokenType.RIGHT_PAREN, "')'");
            }
            return new Ast.Statement(
                    Ast.Statement.Kind.CALL, function.text(), null, arguments);
        }
        throw error(keyword, "Unknown statement '" + keyword.text() + "'",
                "'cooldown', 'call' or 'broadcast'");
    }

    private Token peek() {
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    private Token next() {
        Token token = peek();
        if (index < tokens.size() - 1) {
            index++;
        }
        return token;
    }

    private Token expect(TokenType type, String expected) throws DslException {
        Token token = peek();
        if (!token.is(type)) {
            throw error(token, "Unexpected '" + token.text() + "'", expected);
        }
        return next();
    }

    private Token expectKeyword(String keyword) throws DslException {
        Token token = peek();
        if (!token.isKeyword(keyword)) {
            throw error(token, "Unexpected '" + token.text() + "'", "'" + keyword + "'");
        }
        return next();
    }

    private DslException error(Token token, String message, String expected) {
        return new DslException(file, token.line(), token.column(), message, expected);
    }
}
