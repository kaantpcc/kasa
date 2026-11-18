package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;

/**
 * Thrown when a held account would change outside the hold operations.
 *
 * <p>A held account's balance must always equal the sum of its active holds, so it changes only through
 * {@link LedgerWriter#placeHold(Hold)}, {@link LedgerWriter#captureHold(IdempotencyKey,
 * dev.kaantopcu.kasa.core.account.Account, dev.kaantopcu.kasa.core.money.Money)} and
 * {@link LedgerWriter#releaseHold(IdempotencyKey)}. Posting to it, reversing a hold transaction, or
 * capturing into it is rejected.
 */
public final class HeldAccountPostingException extends LedgerException {

    HeldAccountPostingException(AccountId heldAccountId) {
        super("Held account %s changes only through placeHold, captureHold and releaseHold"
                .formatted(heldAccountId.value()));
    }
}
