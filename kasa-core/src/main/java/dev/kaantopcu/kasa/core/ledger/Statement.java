package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.List;
import java.util.Objects;

/**
 * An account statement for a period: the opening balance, each movement, and the closing balance.
 *
 * <p>Created by {@link LedgerReader#statement(dev.kaantopcu.kasa.core.account.Account, java.time.Instant,
 * java.time.Instant)}.
 *
 * @param openingBalance the balance before the period starts
 * @param lines          the account's entries in the period, in the order they were recorded
 * @param closingBalance the balance at the end of the period
 */
public record Statement(Money openingBalance, List<StatementLine> lines, Money closingBalance) {

    /**
     * Creates a statement.
     *
     * @param openingBalance the balance before the period starts
     * @param lines          the account's entries in the period, in the order they were recorded; copied
     * @param closingBalance the balance at the end of the period
     * @throws IllegalArgumentException if {@code closingBalance} is not the last line's balance, or the opening
     *                                  balance when there are no lines
     */
    public Statement {
        Objects.requireNonNull(openingBalance, "openingBalance");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(closingBalance, "closingBalance");
        lines = List.copyOf(lines);
        requireClosingBalanceMatchesLines(openingBalance, lines, closingBalance);
    }

    private static void requireClosingBalanceMatchesLines(Money openingBalance, List<StatementLine> lines, Money closingBalance) {
        Money expected = lines.isEmpty() ? openingBalance : lines.getLast().balanceAfter();
        if (!expected.equals(closingBalance)) {
            throw new IllegalArgumentException("Closing balance %s %s does not match the statement lines".formatted
                    (closingBalance.amount().toPlainString(), closingBalance.currency().getCurrencyCode()));
        }
    }
}
