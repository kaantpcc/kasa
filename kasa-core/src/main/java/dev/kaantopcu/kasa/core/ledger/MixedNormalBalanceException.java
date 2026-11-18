package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when the accounts under a branch id cannot be summed because some are debit-normal and
 * others credit-normal.
 */
public final class MixedNormalBalanceException extends IllegalArgumentException {

    MixedNormalBalanceException(AccountId branch) {
        super("Accounts under %s have different normal balances".formatted(branch.value()));
    }
}
