package dev.nglmercer.tiktokminecraft.dsl;

/** One token of a script, with the position it was read from. */
public final class Token {

    private final TokenType type;
    private final String text;
    private final int line;
    private final int column;

    public Token(TokenType type, String text, int line, int column) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
    }

    public TokenType type() {
        return type;
    }

    public String text() {
        return text;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public boolean is(TokenType expected) {
        return type == expected;
    }

    public boolean isKeyword(String keyword) {
        return type == TokenType.IDENTIFIER && text.equals(keyword);
    }

    @Override
    public String toString() {
        return type + "('" + text + "') at " + line + ":" + column;
    }
}
