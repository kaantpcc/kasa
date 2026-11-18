package dev.kaantopcu.kasa.core.ledger;

/**
 * Thrown when a hold that was already captured or released is settled in a different way.
 *
 * <p>Repeating exactly the same capture or release is not an error; it has no effect.
 */
public final class HoldAlreadySettledException extends LedgerException {

    HoldAlreadySettledException(IdempotencyKey holdKey) {
        super("Hold %s is already settled".formatted(holdKey.value()));
    }
}
