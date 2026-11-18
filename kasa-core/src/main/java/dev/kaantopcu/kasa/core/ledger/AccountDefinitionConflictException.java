package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when an account id is used with a different definition from the one it was opened with,
 * for example a different currency or balance policy.
 */
public final class AccountDefinitionConflictException extends LedgerException {

    AccountDefinitionConflictException(AccountId accountId) {
        super("Account %s is already open with a different definition".formatted(accountId.value()));
    }
}
