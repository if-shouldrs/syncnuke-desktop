package io.github.syncnuke.session;

public final class SessionException extends RuntimeException {

    public SessionException(Throwable cause) {
        super("Session failed", cause);
    }
}
