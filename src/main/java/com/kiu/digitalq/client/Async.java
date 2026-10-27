package com.kiu.digitalq.client;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Runs blocking network calls off the Event Dispatch Thread. */
public final class Async {

    @FunctionalInterface
    public interface Task<T> {
        T run() throws Exception;
    }

    private Async() {
    }

    public static <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }).whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                onFailure.accept(unwrap(error));
            } else {
                onSuccess.accept(value);
            }
        }));
    }

    private static Exception unwrap(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(cause);
    }
}
