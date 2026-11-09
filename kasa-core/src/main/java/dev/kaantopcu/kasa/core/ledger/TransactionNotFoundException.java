package dev.kaantopcu.kasa.core.ledger;

public final class TransactionNotFoundException extends LedgerException {

    public TransactionNotFoundException(IdempotencyKey key) {
        super("No transaction posted with idempotency key %s".formatted(key.value()));
    }
}
