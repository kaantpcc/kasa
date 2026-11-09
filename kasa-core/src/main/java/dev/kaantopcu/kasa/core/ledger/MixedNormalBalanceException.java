package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public class MixedNormalBalanceException extends IllegalArgumentException {

    public MixedNormalBalanceException(AccountId branch) {
        super("Accounts under %s have different normal balances".formatted(branch.value()));
    }
}
