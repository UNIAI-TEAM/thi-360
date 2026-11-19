package vn.unicom.thi360.exam.spi;

import java.util.Objects;

/**
 * Failure of {@link PaperSolverPort#generate}. The port never returns a partial paper: it either returns every
 * requested question or throws this exception. {@link #reason()} is the value the caller stores as the reason of
 * an aborted attempt.
 */
public final class PaperGenerationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** The request is not valid for the version, the source cannot fill a quota, or generation broke. */
        GENERATION_FAILED,
        /** The generation deadline ({@code thi360.exam.solver.generate.deadline}) ran out. */
        GENERATION_TIMEOUT
    }

    private final Reason reason;

    public PaperGenerationException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public PaperGenerationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
