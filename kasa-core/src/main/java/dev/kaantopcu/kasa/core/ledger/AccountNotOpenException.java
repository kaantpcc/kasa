package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public final class AccountNotOpenException extends LedgerException {

    public AccountNotOpenException(AccountId accountId) {
        super("Account %s has not been opened".formatted(accountId.value()));
    }
}
