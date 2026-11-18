package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when money would move into or out of a closed account, or a closed account would be
 * reopened, frozen or unfrozen.
 */
public final class AccountClosedException extends LedgerException {

    AccountClosedException(AccountId accountId) {
        super("Account %s is closed".formatted(accountId.value()));
    }
}
