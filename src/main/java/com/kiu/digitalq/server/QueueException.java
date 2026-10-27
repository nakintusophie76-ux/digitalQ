package com.kiu.digitalq.server;

/** A validation or business-rule problem that should be shown to the user. */
public class QueueException extends RuntimeException {

    public QueueException(String message) {
        super(message);
    }

    public QueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
