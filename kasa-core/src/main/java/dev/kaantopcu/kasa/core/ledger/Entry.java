package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

/**
 * One line of a transaction: an amount debited or credited to an account.
 *
 * <p>The amount is always positive; the side gives the direction.
 *
 * @param account the account the entry posts to
 * @param amount  the positive amount, in the account's currency
 * @param side    whether the entry is a debit or a credit
 */
public record Entry(Account account, Money amount, Side side) {

    /**
     * Creates an entry.
     *
     * @param account the account the entry posts to
     * @param amount  the positive amount, in the account's currency
     * @param side    whether the entry is a debit or a credit
     * @throws InvalidEntryAmountException if {@code amount} is zero or negative
     * @throws CurrencyMismatchException   if {@code amount} is not in the account's currency
     */
    public Entry {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        requirePositive(amount);
        requireAccountCurrency(account, amount);
    }

    /**
     * Returns the same entry on the opposite side, which cancels this one.
     *
     * @return the entry with the same account and amount on the other side
     */
    public Entry reversed() {
        return new Entry(account, amount, side.opposite());
    }

    /**
     * Returns how this entry changes the account's balance.
     *
     * @return the amount if the entry is on the account's normal balance side, otherwise its negation
     */
    public Money balanceChange() {
        if (side == account.normalBalance()) {
            return amount;
        }
        return amount.negate();
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidEntryAmountException(amount);
        }
    }

    private static void requireAccountCurrency(Account account, Money amount) {
        if (!account.currency().equals(amount.currency())) {
            throw new CurrencyMismatchException(account.currency(), amount.currency());
        }
    }
}
