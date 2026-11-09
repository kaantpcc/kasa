package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public class AccountNotOpenException extends IllegalArgumentException {

    public AccountNotOpenException(AccountId accountId) {
        super("Account %s has not been opened".formatted(accountId.value()));
    }
}
