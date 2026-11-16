package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

public record TrialBalanceLine(Account account, Money debits, Money credits) {

    public TrialBalanceLine {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(debits, "debits");
        Objects.requireNonNull(credits, "credits");
        requireAccountCurrency(account, debits);
        requireAccountCurrency(account, credits);
    }

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
