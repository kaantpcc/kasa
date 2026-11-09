package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public final class AccountClosedException extends LedgerException {

    public AccountClosedException(AccountId accountId) {
        super("Account %s is closed".formatted(accountId.value()));
    }
}
