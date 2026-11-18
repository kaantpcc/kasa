package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when an account, or every account under a branch id, has not been opened in the ledger.
 */
public final class AccountNotOpenException extends LedgerException {

    AccountNotOpenException(AccountId accountId) {
        super("Account %s has not been opened".formatted(accountId.value()));
    }
}
