package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;

/**
 * Thrown when an account that already holds money is about to become the held account of a holdable account.
 *
 * <p>A held account's balance must always equal the sum of its active holds. Money that reached it some other
 * way could never leave, because a held account changes only through the hold operations.
 */
public final class HeldAccountNotEmptyException extends LedgerException {

    HeldAccountNotEmptyException(AccountId heldAccountId, Money balance) {
        super("Held account %s must start with a zero balance, got %s %s".formatted(
                heldAccountId.value(),
                balance.amount().toPlainString(),
                balance.currency().getCurrencyCode()));
    }
}
