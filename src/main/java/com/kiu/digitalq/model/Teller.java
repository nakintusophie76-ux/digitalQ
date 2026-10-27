package com.kiu.digitalq.model;

import java.time.LocalDateTime;

/** A teller (counter operator) created by the administrator. */
public final class Teller {

    public final String id;
    public String name;
    public int counter;
    public String pin;
    public boolean enabled = true;

    public TellerStatus status = TellerStatus.AVAILABLE;
    public LocalDateTime statusSince = LocalDateTime.now();
    public Integer currentTicket;
    public int servedToday;
    public long lastAssignedSeq;

    public Teller(String id, String name, int counter, String pin) {
        this.id = id;
        this.name = name;
        this.counter = counter;
        this.pin = pin;
    }

    public boolean isBusy() {
        return currentTicket != null;
    }
}
