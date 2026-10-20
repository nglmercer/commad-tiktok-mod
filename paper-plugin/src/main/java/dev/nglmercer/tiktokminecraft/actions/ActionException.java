package dev.nglmercer.tiktokminecraft.actions;

/** Why an action could not be validated or executed. */
public class ActionException extends Exception {

    private static final long serialVersionUID = 1L;

    public ActionException(String message) {
        super(message);
    }

    public ActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
