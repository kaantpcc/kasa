package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

/**
 * Thrown when an entry amount is zero or negative. The direction of an entry is given by its side,
 * never by the sign of its amount.
 */
public final class InvalidEntryAmountException extends IllegalArgumentException {

    InvalidEntryAmountException(Money amount) {
        super("Entry amount must be positive, got %s %s".formatted(
                amount.amount().toPlainString(),
                amount.currency().getCurrencyCode()));
    }
}
