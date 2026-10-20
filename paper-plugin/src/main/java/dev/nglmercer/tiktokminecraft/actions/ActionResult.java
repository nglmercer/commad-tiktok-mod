package dev.nglmercer.tiktokminecraft.actions;

/** The outcome of running an action. */
public final class ActionResult {

    /** How the action ended. */
    public enum Status {
        /** The action ran. */
        OK,
        /** The action was deliberately not run. */
        SKIPPED,
        /** The action ran but failed. */
        FAILED
    }

    private final Status status;
    private final String message;

    private ActionResult(Status status, String message) {
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public static ActionResult ok(String message) {
        return new ActionResult(Status.OK, message);
    }

    public static ActionResult skipped(String message) {
        return new ActionResult(Status.SKIPPED, message);
    }

    public static ActionResult failed(String message) {
        return new ActionResult(Status.FAILED, message);
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public boolean isSuccess() {
        return status == Status.OK;
    }

    @Override
    public String toString() {
        return status + ": " + message;
    }
}
