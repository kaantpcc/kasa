package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Map;

final class RefundLimits {

    private final Map<Account, Money> originalChanges;
    private final Map<Account, Money> refundedChanges;

    RefundLimits(Map<Account, Money> originalChanges, Map<Account, Money> refundedChanges) {
        this.originalChanges = Map.copyOf(originalChanges);
        this.refundedChanges = Map.copyOf(refundedChanges);
    }

    void requireRefundable(Account account, Money change) {
        Money zero = Money.zero(account.currency());
        Money originalChange = originalChanges.getOrDefault(account, zero);
        Money alreadyRefunded = inRefundDirection(refundedChanges.getOrDefault(account, zero), originalChange);
        Money refundable = inRefundDirection(originalChange.negate(), originalChange).subtract(alreadyRefunded);
        Money requested = inRefundDirection(change, originalChange);
        if (requested.isNegative() || requested.isGreaterThan(refundable)) {
            throw new RefundExceedsOriginalException(account.id(), refundable, requested);
        }
    }

    private static Money inRefundDirection(Money amount, Money originalChange) {
        if (originalChange.isNegative()) {
            return amount;
        }
        return amount.negate();
    }
}
