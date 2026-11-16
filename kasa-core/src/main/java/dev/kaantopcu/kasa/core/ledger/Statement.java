package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.List;
import java.util.Objects;

public record Statement(Money openingBalance, List<StatementLine> lines, Money closingBalance) {

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
