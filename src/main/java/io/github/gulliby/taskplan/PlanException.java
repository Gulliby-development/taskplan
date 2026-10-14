package io.github.gulliby.taskplan;

/** A user-facing planning error whose message is ready to print. */
public final class PlanException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlanException(String message) {
        super(message);
    }
}
