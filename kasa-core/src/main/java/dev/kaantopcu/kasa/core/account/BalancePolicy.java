package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

/**
 * How far an account's balance may go below zero.
 *
 * <p>The ledger checks the policy before posting any transaction that lowers the balance, and rejects the
 * transaction with {@link dev.kaantopcu.kasa.core.ledger.InsufficientFundsException} if it would break the
 * policy. There is no default policy; every account chooses one.
 */
public sealed interface BalancePolicy {

    /**
     * Returns a policy with no lower limit, typical for system accounts such as customer payments.
     *
     * @return the unlimited policy
     */
    static BalancePolicy unlimited() {
        return new Unlimited();
    }

    /**
     * Returns a policy that keeps the balance at zero or above, typical for wallets.
     *
     * @return the non-negative policy
     */
    static BalancePolicy nonNegative() {
        return new NonNegative();
    }

    /**
     * Returns a policy that lets the balance go below zero down to the negative of a limit.
     *
     * @param limit the largest negative balance allowed, as a positive amount in the account's currency
     * @return the overdraft policy
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    static BalancePolicy overdraft(Money limit) {
        return new Overdraft(limit);
    }

    /**
     * The balance may go below zero without limit.
     */
    record Unlimited() implements BalancePolicy {
    }

    /**
     * The balance may not go below zero.
     */
    record NonNegative() implements BalancePolicy {
    }

    /**
     * The balance may go below zero down to the negative of a limit.
     *
     * @param limit the largest negative balance allowed, as a positive amount
     */
    record Overdraft(Money limit) implements BalancePolicy {

        /**
         * Creates an overdraft policy.
         *
         * @param limit the largest negative balance allowed, as a positive amount
         * @throws IllegalArgumentException if {@code limit} is not positive
         */
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
