package dev.kaantopcu.kasa.core.ledger;

public class TransactionNotFoundException extends IllegalArgumentException {

    public TransactionNotFoundException(IdempotencyKey key) {
        super("No transaction posted with idempotency key %s".formatted(key.value()));
    }
}
