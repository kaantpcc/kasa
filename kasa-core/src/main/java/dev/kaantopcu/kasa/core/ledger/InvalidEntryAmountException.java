package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

public class InvalidEntryAmountException extends IllegalArgumentException {

    public InvalidEntryAmountException(Money amount) {
        super("Entry amount must be positive, got %s %s".formatted(
                amount.amount().toPlainString(),
                amount.currency().getCurrencyCode()));
    }
}
