package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public final class AccountFrozenException extends LedgerException {

    public AccountFrozenException(AccountId accountId) {
        super("Account %s is frozen".formatted(accountId.value()));
    }
}
