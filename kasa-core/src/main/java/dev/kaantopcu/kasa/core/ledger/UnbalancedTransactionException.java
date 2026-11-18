package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

/**
 * Thrown when the debits and credits of a transaction differ in some currency.
 */
public final class UnbalancedTransactionException extends IllegalArgumentException {

    UnbalancedTransactionException(Money difference) {
        super("Transaction is unbalanced in %s: debits minus credits = %s".formatted(
                difference.currency().getCurrencyCode(),
                difference.amount().toPlainString()));
    }
}
