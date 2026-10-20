package dev.nglmercer.tiktokminecraft.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a script into tokens.
 *
 * <p>The lexer is deliberately small: it knows words, quoted strings,
 * numbers, durations and the few operators the grammar uses. It has no
 * concept of what a word means, so it can never execute anything.</p>
 */
public final class Lexer {

    private final String source;
    private final String file;
    private int index;
    private int line = 1;
    private int column = 1;

    private Lexer(String source, String file) {
        this.source = source;
        this.file = file;
    }

    /** Reads a whole script. */
    public static List<Token> tokenize(String source, String file) throws DslException {
        return new Lexer(source == null ? "" : source, file).run();
    }

    private List<Token> run() throws DslException {
        List<Token> tokens = new ArrayList<Token>();
        while (true) {
            skipWhitespace();
            if (index >= source.length()) {
                tokens.add(new Token(TokenType.END, "", line, column));
                return tokens;
            }
            tokens.add(readToken());
        }
    }

    private void skipWhitespace() {
        while (index < source.length()) {
            char character = source.charAt(index);
            if (character == '\n') {
                index++;
                line++;
                column = 1;
            } else if (Character.isWhitespace(character)) {
                index++;
                column++;
            } else if (character == '#') {
                // A comment runs to the end of the line.
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                    column++;
                }
            } else {
                return;
            }
        }
    }

    private Token readToken() throws DslException {
        int startLine = line;
        int startColumn = column;
        char character = source.charAt(index);
        switch (character) {
            case '(':
                advance();
                return new Token(TokenType.LEFT_PAREN, "(", startLine, startColumn);
            case ')':
                advance();
                return new Token(TokenType.RIGHT_PAREN, ")", startLine, startColumn);
            case '{':
                advance();
                return new Token(TokenType.LEFT_BRACE, "{", startLine, startColumn);
            case '}':
                advance();
                return new Token(TokenType.RIGHT_BRACE, "}", startLine, startColumn);
            case ',':
                advance();
                return new Token(TokenType.COMMA, ",", startLine, startColumn);
            case '=':
                advance();
                if (index < source.length() && source.charAt(index) == '=') {
                    advance();
                    return new Token(TokenType.EQUAL, "==", startLine, startColumn);
                }
                return new Token(TokenType.ASSIGN, "=", startLine, startColumn);
            case '!':
                advance();
                if (index < source.length() && source.charAt(index) == '=') {
                    advance();
                    return new Token(TokenType.NOT_EQUAL, "!=", startLine, startColumn);
                }
                throw error(startLine, startColumn, "'!' is only valid as '!='", "'!='");
            case '>':
                advance();
                if (index < source.length() && source.charAt(index) == '=') {
                    advance();
                    return new Token(TokenType.GREATER_OR_EQUAL, ">=", startLine, startColumn);
                }
                return new Token(TokenType.GREATER, ">", startLine, startColumn);
            case '<':
                advance();
                if (index < source.length() && source.charAt(index) == '=') {
                    advance();
                    return new Token(TokenType.LESS_OR_EQUAL, "<=", startLine, startColumn);
                }
                return new Token(TokenType.LESS, "<", startLine, startColumn);
            case '"':
            case '\'':
                return readString(character, startLine, startColumn);
            default:
                break;
        }
        if (Character.isDigit(character)) {
            return readNumber(startLine, startColumn);
        }
        if (isIdentifierStart(character)) {
            return readIdentifier(startLine, startColumn);
        }
        throw error(startLine, startColumn, "Unexpected character '" + character + "'", null);
    }

    private Token readString(char quote, int startLine, int startColumn) throws DslException {
        advance();
        StringBuilder builder = new StringBuilder();
        while (index < source.length()) {
            char character = source.charAt(index);
            if (character == '\\' && index + 1 < source.length()) {
                char escaped = source.charAt(index + 1);
                switch (escaped) {
                    case 'n':
                        builder.append('\n');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case '\\':
                        builder.append('\\');
                        break;
                    case '"':
                        builder.append('"');
                        break;
                    case '\'':
                        builder.append('\'');
                        break;
                    default:
                        builder.append('\\').append(escaped);
                        break;
                }
                advance();
                advance();
                continue;
            }
            if (character == quote) {
                advance();
                return new Token(TokenType.STRING, builder.toString(), startLine, startColumn);
            }
            if (character == '\n') {
                throw error(startLine, startColumn, "The string is not closed", "a closing quote");
            }
            builder.append(character);
            advance();
        }
        throw error(startLine, startColumn, "The string is not closed", "a closing quote");
    }

    private Token readNumber(int startLine, int startColumn) throws DslException {
        StringBuilder builder = new StringBuilder();
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            builder.append(source.charAt(index));
            advance();
        }
        // A number may be followed by a duration unit.
        if (index < source.length() && isIdentifierStart(source.charAt(index))) {
            StringBuilder unit = new StringBuilder();
            while (index < source.length() && isIdentifierPart(source.charAt(index))) {
                unit.append(source.charAt(index));
                advance();
            }
            String suffix = unit.toString();
            if (suffix.equals("ms") || suffix.equals("s") || suffix.equals("m")
                    || suffix.equals("h") || suffix.equals("d")) {
                return new Token(TokenType.DURATION, builder + suffix, startLine, startColumn);
            }
            throw error(startLine, startColumn, "Unknown unit '" + suffix + "'",
                    "ms, s, m, h or d");
        }
        return new Token(TokenType.NUMBER, builder.toString(), startLine, startColumn);
    }

    private Token readIdentifier(int startLine, int startColumn) {
        StringBuilder builder = new StringBuilder();
        while (index < source.length() && isIdentifierPart(source.charAt(index))) {
            builder.append(source.charAt(index));
            advance();
        }
        return new Token(TokenType.IDENTIFIER, builder.toString(), startLine, startColumn);
    }

    private static boolean isIdentifierStart(char character) {
        return Character.isLetter(character) || character == '_' || character == '.'
                || character == ':' || character == '-';
    }

    private static boolean isIdentifierPart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '.'
                || character == ':' || character == '-';
    }

    private void advance() {
        index++;
        column++;
    }

    private DslException error(int atLine, int atColumn, String message, String expected) {
        return new DslException(file, atLine, atColumn, message, expected);
    }
}
