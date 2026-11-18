package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

/**
 * Thrown when an account with a non-zero balance is closed. Move the money out first.
 */
public final class NonZeroBalanceException extends LedgerException {

    NonZeroBalanceException(AccountId accountId, Money balance) {
        super("Cannot close account %s with non-zero balance %s %s".formatted(
                accountId.value(),
                balance.amount().toPlainString(),
                balance.currency().getCurrencyCode()));
    }
}
