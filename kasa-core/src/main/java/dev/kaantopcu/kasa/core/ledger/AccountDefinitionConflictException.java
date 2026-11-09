package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public final class AccountDefinitionConflictException extends LedgerException {

    public AccountDefinitionConflictException(AccountId accountId) {
        super("Account %s is already open with a different definition".formatted(accountId.value()));
    }
}
