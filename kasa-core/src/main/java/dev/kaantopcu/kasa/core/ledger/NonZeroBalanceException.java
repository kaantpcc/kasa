package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

public class NonZeroBalanceException extends IllegalStateException {

    public NonZeroBalanceException(AccountId accountId, Money balance) {
        super("Cannot close account %s with non-zero balance %s %s".formatted(
                accountId.value(),
                balance.amount().toPlainString(),
                balance.currency().getCurrencyCode()));
    }
}
