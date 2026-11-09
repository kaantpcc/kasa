package dev.kaantopcu.kasa.core.ledger;

public class IdempotencyKeyConflictException extends IllegalStateException {

    public IdempotencyKeyConflictException(IdempotencyKey key) {
        super("Idempotency key %s was already used for a different transaction".formatted(key.value()));
    }
}
