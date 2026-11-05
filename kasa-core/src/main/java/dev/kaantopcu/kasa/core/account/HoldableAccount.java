package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;

import java.util.Objects;

public record HoldableAccount(Account available, Account held) {

    public HoldableAccount {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(held, "held");
        requireDifferentAccounts(available, held);
        requireSameCurrency(available, held);
        requireSameNormalBalance(available, held);
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
}
