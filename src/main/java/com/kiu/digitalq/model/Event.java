package com.kiu.digitalq.model;

import java.time.LocalDateTime;

/** An entry in the activity ticker (issuing, calling, completing, resetting...). */
public final class Event {

    public final LocalDateTime at;
    public final String text;
    public final String kind;

    public Event(LocalDateTime at, String text, String kind) {
        this.at = at;
        this.text = text;
        this.kind = kind == null ? "INFO" : kind;
    }
}
