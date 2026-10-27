package com.kiu.digitalq.model;

/** Lifecycle of a single queue ticket. */
public enum TicketState {

    WAITING("Waiting"),
    SERVING("Being served"),
    DONE("Completed"),
    MISSED("Missed");

    private final String label;

    TicketState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TicketState parse(String value) {
        if (value != null) {
            for (TicketState state : values()) {
                if (state.name().equalsIgnoreCase(value.trim())) {
                    return state;
                }
            }
        }
        return WAITING;
    }
}
