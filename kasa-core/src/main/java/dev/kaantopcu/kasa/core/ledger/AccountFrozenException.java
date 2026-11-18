package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when money would leave a frozen account. A frozen account can still receive money.
 */
public final class AccountFrozenException extends LedgerException {

    AccountFrozenException(AccountId accountId) {
        super("Account %s is frozen".formatted(accountId.value()));
    }
}
