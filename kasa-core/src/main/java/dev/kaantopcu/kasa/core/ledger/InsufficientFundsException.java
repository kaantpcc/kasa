package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

/**
 * Thrown when a transaction would take an account's balance below what its
 * {@link dev.kaantopcu.kasa.core.account.BalancePolicy} allows.
 */
public final class InsufficientFundsException extends LedgerException {

    InsufficientFundsException(AccountId accountId, Money available, Money requested) {
        super("Insufficient available balance in %s: available %s %s, requested %s %s".formatted(
                accountId.value(),
                available.amount().toPlainString(),
                available.currency().getCurrencyCode(),
                requested.amount().toPlainString(),
                requested.currency().getCurrencyCode()));
    }
}
