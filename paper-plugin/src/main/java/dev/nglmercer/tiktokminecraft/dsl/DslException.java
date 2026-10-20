package dev.nglmercer.tiktokminecraft.dsl;

/** A problem in a script, with the position that caused it. */
public class DslException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String file;
    private final int line;
    private final int column;
    private final String expected;

    public DslException(String file, int line, int column, String message, String expected) {
        super(build(file, line, column, message, expected));
        this.file = file;
        this.line = line;
        this.column = column;
        this.expected = expected;
    }

    private static String build(
            String file, int line, int column, String message, String expected) {
        StringBuilder builder = new StringBuilder();
        if (file != null && !file.isEmpty()) {
            builder.append(file).append(':');
        }
        builder.append(line).append(':').append(column).append(": ").append(message);
        if (expected != null && !expected.isEmpty()) {
            builder.append("; expected ").append(expected);
        }
        return builder.toString();
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    /** What the parser expected, or null. */
    public String expected() {
        return expected;
    }
}
