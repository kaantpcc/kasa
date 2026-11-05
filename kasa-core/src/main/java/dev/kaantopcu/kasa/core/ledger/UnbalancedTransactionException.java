package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

public class UnbalancedTransactionException extends IllegalArgumentException {

    public UnbalancedTransactionException(Money difference) {
        super("Transaction is unbalanced in %s: debits minus credits = %s".formatted(
                difference.currency().getCurrencyCode(),
                difference.amount().toPlainString()));
    }
}
