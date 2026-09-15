package io.github.syncnuke.player;

public final class PlayerConnectionException extends RuntimeException {

    public PlayerConnectionException(String message) {
        super(message);
    }

    public PlayerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
