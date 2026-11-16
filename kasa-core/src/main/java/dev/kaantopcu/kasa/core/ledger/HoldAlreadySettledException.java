package dev.kaantopcu.kasa.core.ledger;

public final class HoldAlreadySettledException extends LedgerException {

    public HoldAlreadySettledException(IdempotencyKey holdKey) {
        super("Hold %s is already settled".formatted(holdKey.value()));
    }
}
