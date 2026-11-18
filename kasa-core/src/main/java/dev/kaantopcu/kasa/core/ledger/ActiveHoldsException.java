package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when an account is closed while holds placed on it are still active.
 *
 * <p>Releasing a hold returns its money to the available account, so that account must stay open until every
 * hold is captured or released.
 */
public final class ActiveHoldsException extends LedgerException {

    ActiveHoldsException(AccountId accountId) {
        super("Cannot close account %s while it has active holds".formatted(accountId.value()));
    }
}
