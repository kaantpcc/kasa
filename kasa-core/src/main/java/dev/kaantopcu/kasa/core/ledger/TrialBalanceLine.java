package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

/**
 * The debit and credit totals of one account in a {@link TrialBalance}.
 *
 * @param account the account
 * @param debits  the sum of the account's debit entries
 * @param credits the sum of the account's credit entries
 */
public record TrialBalanceLine(Account account, Money debits, Money credits) {

    /**
     * Creates a trial balance line.
     *
     * @param account the account
     * @param debits  the sum of the account's debit entries, in its currency
     * @param credits the sum of the account's credit entries, in its currency
     * @throws CurrencyMismatchException if {@code debits} or {@code credits} is not in the account's currency
     */
    public TrialBalanceLine {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(debits, "debits");
        Objects.requireNonNull(credits, "credits");
        requireAccountCurrency(account, debits);
        requireAccountCurrency(account, credits);
    }

    /**
     * Returns the account's balance in its normal direction.
     *
     * @return debits minus credits for a debit-normal account, credits minus debits for a credit-normal one
     */
    public Money balance() {
        return switch (account.normalBalance()) {
            case DEBIT -> debits.subtract(credits);
            case CREDIT -> credits.subtract(debits);
        };
    }

    private static void requireAccountCurrency(Account account, Money amount) {
        if (!account.currency().equals(amount.currency())) {
            throw new CurrencyMismatchException(account.currency(), amount.currency());
        }
    }
}
