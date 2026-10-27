package com.kiu.digitalq.model;

import java.time.LocalDateTime;

/** A student's queue ticket for one day. */
public final class Ticket {

    public final int number;
    public final String regNo;
    public final LocalDateTime issuedAt;

    public TicketState state = TicketState.WAITING;
    public String tellerId;
    public LocalDateTime calledAt;
    public LocalDateTime closedAt;
    public int recalls;

    public Ticket(int number, String regNo, LocalDateTime issuedAt) {
        this.number = number;
        this.regNo = regNo;
        this.issuedAt = issuedAt;
    }

    /** Three digit queue token, for example {@code 000} or {@code 042}. */
    public String token() {
        return String.format("%03d", number);
    }
}
