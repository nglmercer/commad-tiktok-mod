package dev.nglmercer.tiktokminecraft.dsl;

/** The kinds of token a script is made of. */
public enum TokenType {
    /** A word: a keyword, a trigger, a field or a function id. */
    IDENTIFIER,
    /** A quoted string. */
    STRING,
    /** A whole number. */
    NUMBER,
    /** A duration such as {@code 10s}. */
    DURATION,
    /** {@code ==} */
    EQUAL,
    /** {@code !=} */
    NOT_EQUAL,
    /** {@code >} */
    GREATER,
    /** {@code >=} */
    GREATER_OR_EQUAL,
    /** {@code <} */
    LESS,
    /** {@code <=} */
    LESS_OR_EQUAL,
    /** {@code (} */
    LEFT_PAREN,
    /** {@code )} */
    RIGHT_PAREN,
    /** <code>{</code> */
    LEFT_BRACE,
    /** <code>}</code> */
    RIGHT_BRACE,
    /** {@code ,} */
    COMMA,
    /** {@code =} */
    ASSIGN,
    /** The end of the script. */
    END
}
