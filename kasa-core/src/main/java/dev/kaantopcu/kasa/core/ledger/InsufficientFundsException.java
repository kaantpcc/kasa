package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

public final class InsufficientFundsException extends LedgerException {

    public InsufficientFundsException(AccountId accountId, Money available, Money requested) {
        super("Insufficient available balance in %s: available %s %s, requested %s %s".formatted(
                accountId.value(),
                available.amount().toPlainString(),
                available.currency().getCurrencyCode(),
                requested.amount().toPlainString(),
                requested.currency().getCurrencyCode()));
    }
}
