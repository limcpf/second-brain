package com.my.brain.adapter.in.idempotency;

public interface IdempotencyStore {

    boolean isProcessed(String eventId);

    void markProcessed(String eventId);
}
