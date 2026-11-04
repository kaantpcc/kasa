package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.Side;

import java.util.Objects;

public record Entry(Account account, Money amount, Side side) {

    public Entry {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        requirePositive(amount);
        requireAccountCurrency(account, amount);
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
