package dev.kaantopcu.kasa.core.ledger;

/**
 * Thrown when an idempotency key that was already used is posted again with a different transaction.
 *
 * <p>Posting exactly the same transaction with the same key again is not an error; it has no effect.
 */
public final class IdempotencyKeyConflictException extends LedgerException {

    IdempotencyKeyConflictException(IdempotencyKey key) {
        super("Idempotency key %s was already used for a different transaction".formatted(key.value()));
    }
}
