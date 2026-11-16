package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.Currency;
import java.util.List;
import java.util.Objects;

public record TrialBalance(List<TrialBalanceLine> lines) {

    public TrialBalance {
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }

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
