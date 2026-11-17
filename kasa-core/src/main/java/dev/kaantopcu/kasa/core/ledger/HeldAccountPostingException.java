package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

public final class HeldAccountPostingException extends LedgerException {

    public HeldAccountPostingException(AccountId heldAccountId) {
        super("Held account %s changes only through placeHold, captureHold and releaseHold"
                .formatted(heldAccountId.value()));
    }
}
