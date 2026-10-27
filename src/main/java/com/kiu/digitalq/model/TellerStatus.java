package com.kiu.digitalq.model;

/** Availability of a teller / counter. */
public enum TellerStatus {

    AVAILABLE("Ready", "Available for the next student"),
    SERVING("Serving", "Attending to a student"),
    RESTING("Resting", "On a short rest, not receiving students"),
    NOT_AROUND("Not around", "Temporarily away from the counter"),
    CLOSED("Counter closed", "Closed for today");

    private final String label;
    private final String hint;

    TellerStatus(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    public boolean canReceiveStudents() {
        return this == AVAILABLE;
    }

    public static TellerStatus parse(String value) {
        if (value != null) {
            for (TellerStatus status : values()) {
                if (status.name().equalsIgnoreCase(value.trim())) {
                    return status;
                }
            }
        }
        return null;
    }
}
