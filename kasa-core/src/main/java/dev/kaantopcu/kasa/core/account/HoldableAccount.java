package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;

import java.util.Objects;

/**
 * An account that can put money on hold, made of an available account and a held account.
 *
 * <p>Placing a hold moves money from the available account to the held account, so it can no longer be
 * spent but still belongs to the owner. The total balance is the sum of both. Open it with
 * {@link dev.kaantopcu.kasa.core.ledger.AccountAdministration#openHoldable(HoldableAccount)}; after that,
 * the held account changes only through the ledger's hold operations.
 *
 * @param available the account the owner can spend from
 * @param held      the account that keeps money on hold
 */
public record HoldableAccount(Account available, Account held) {

    /**
     * Creates a holdable account from two account definitions.
     *
     * @param available the account the owner can spend from
     * @param held      the account that keeps money on hold
     * @throws IllegalArgumentException  if both accounts have the same id, their normal balances differ, or
     *                                   the held account's policy is not {@link BalancePolicy#nonNegative()}
     * @throws CurrencyMismatchException if the two accounts are in different currencies
     */
    public HoldableAccount {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(held, "held");
        requireDifferentAccounts(available, held);
        requireSameCurrency(available, held);
        requireSameNormalBalance(available, held);
        requireNonNegativeHeldAccount(held);
    }

    private static void requireDifferentAccounts(Account available, Account held) {
        if (available.id().equals(held.id())) {
            throw new IllegalArgumentException("Held account must differ from available account, got %s".formatted(available.id().value()));
        }
    }

    private static void requireSameCurrency(Account available, Account held) {
        if (!available.currency().equals(held.currency())) {
            throw new CurrencyMismatchException(available.currency(), held.currency());
        }
    }

    private static void requireSameNormalBalance(Account available, Account held) {
        if (available.normalBalance() != held.normalBalance()) {
            throw new IllegalArgumentException("Held account must have the same normal balance as available account");
        }
    }

    private static void requireNonNegativeHeldAccount(Account held) {
        if (!(held.balancePolicy() instanceof BalancePolicy.NonNegative)) {
            throw new IllegalArgumentException("Held account %s must have a non-negative balance policy".formatted(held.id().value()));
        }
    }
}
