package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

public sealed interface BalancePolicy {

    static BalancePolicy unlimited() {
        return new Unlimited();
    }

    static BalancePolicy nonNegative() {
        return new NonNegative();
    }

    static BalancePolicy overdraft(Money limit) {
        return new Overdraft(limit);
    }

    record Unlimited() implements BalancePolicy {
    }

    record NonNegative() implements BalancePolicy {
    }

    record Overdraft(Money limit) implements BalancePolicy {

        public Overdraft {
            Objects.requireNonNull(limit, "limit");
            requirePositive(limit);
        }

        private static void requirePositive(Money limit) {
            if (!limit.isPositive()) {
                throw new IllegalArgumentException("Overdraft limit must be positive, got %s %s".formatted(
                        limit.amount(), limit.currency().getCurrencyCode()));
            }
        }
    }
}
