package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonObject;

import javax.swing.Timer;
import java.util.function.Consumer;

/**
 * Repeating background refresh. Calls never overlap: while one request is in
 * flight the next tick is skipped, so a slow server cannot pile up requests.
 */
public final class Poller {

    private final Timer timer;
    private final Async.Task<JsonObject> task;
    private final Consumer<JsonObject> onData;
    private final Consumer<Exception> onError;
    private boolean busy;
    private boolean stopped = true;

    public Poller(int intervalMillis, Async.Task<JsonObject> task,
                  Consumer<JsonObject> onData, Consumer<Exception> onError) {
        this.task = task;
        this.onData = onData;
        this.onError = onError;
        this.timer = new Timer(Math.max(250, intervalMillis), event -> refresh());
        this.timer.setInitialDelay(0);
    }

    public void start() {
        stopped = false;
        timer.start();
    }

    public void startDelayed(int delayMillis) {
        stopped = false;
        timer.setInitialDelay(Math.max(0, delayMillis));
        timer.start();
    }

    public void stop() {
        stopped = true;
        timer.stop();
    }

    public void refreshNow() {
        if (!stopped) {
            timer.restart();
        }
    }

    private void refresh() {
        if (busy || stopped) {
            return;
        }
        busy = true;
        Async.run(task, data -> {
            busy = false;
            if (!stopped) {
                onData.accept(data);
            }
        }, error -> {
            busy = false;
            if (!stopped) {
                onError.accept(error);
            }
        });
    }
}
