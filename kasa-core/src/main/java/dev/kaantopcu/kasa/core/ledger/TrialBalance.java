package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * The debit and credit totals of every opened account at a point in time.
 *
 * <p>In a correct double-entry ledger, total debits equal total credits in every currency.
 * Created by {@link LedgerReader#trialBalance(java.time.Instant)}.
 *
 * @param lines one line per opened account, in the order the accounts were opened
 */
public record TrialBalance(List<TrialBalanceLine> lines) {

    /**
     * Creates a trial balance.
     *
     * @param lines one line per account; copied
     */
    public TrialBalance {
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }

    /**
     * Returns the sum of debits of all accounts in a currency.
     *
     * @param currency the currency to total
     * @return the total, or zero if no account uses the currency
     */
    public Money totalDebits(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        Money total = Money.zero(currency);
        for (TrialBalanceLine line : lines) {
            if (line.account().currency().equals(currency)) {
                total = total.add(line.debits());
            }
        }
        return total;
    }

    /**
     * Returns the sum of credits of all accounts in a currency.
     *
     * @param currency the currency to total
     * @return the total, or zero if no account uses the currency
     */
    public Money totalCredits(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        Money total = Money.zero(currency);
        for (TrialBalanceLine line : lines) {
            if (line.account().currency().equals(currency)) {
                total = total.add(line.credits());
            }
        }
        return total;
    }

    /**
     * Tells whether total debits equal total credits in every currency.
     *
     * @return {@code true} if the ledger balances
     */
    public boolean isBalanced() {
        for (TrialBalanceLine line : lines) {
            Currency currency = line.account().currency();
            if (!totalDebits(currency).equals(totalCredits(currency))) {
                return false;
            }
        }
        return true;
    }
}
