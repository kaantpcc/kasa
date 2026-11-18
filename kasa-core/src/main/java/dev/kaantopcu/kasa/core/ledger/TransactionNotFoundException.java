package dev.kaantopcu.kasa.core.ledger;

/**
 * Thrown when no transaction or hold has been posted with the given idempotency key.
 */
public final class TransactionNotFoundException extends LedgerException {

    TransactionNotFoundException(IdempotencyKey key) {
        super("No transaction posted with idempotency key %s".formatted(key.value()));
    }
}
