package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

public final class RefundExceedsOriginalException extends LedgerException {

    public RefundExceedsOriginalException(AccountId accountId, Money refundable, Money requested) {
        super("Refund exceeds what can be refunded on %s: refundable %s %s, requested %s %s".formatted(
                accountId.value(),
                refundable.amount().toPlainString(),
                refundable.currency().getCurrencyCode(),
                requested.amount().toPlainString(),
                requested.currency().getCurrencyCode()));
    }
}
