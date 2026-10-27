package com.kiu.digitalq.client;

/** The signed-in teller and their server session token. */
public record TellerSession(String token, String tellerId, String name, int counter) {
}
