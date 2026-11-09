package dev.kaantopcu.kasa.core.ledger;

public final class IdempotencyKeyConflictException extends LedgerException {

    public IdempotencyKeyConflictException(IdempotencyKey key) {
        super("Idempotency key %s was already used for a different transaction".formatted(key.value()));
    }
}
