package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.List;
import java.util.Objects;

public record Hold(IdempotencyKey key, HoldableAccount account, Money amount) {

    public Hold {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        requireAccountCurrency(account, amount);
    }

    public Transaction place() {
        return new Transaction(key, List.of(
                decrease(account.available()),
                increase(account.held())));
    }

    private Entry decrease(Account target) {
        return new Entry(target, amount, target.normalBalance().opposite());
    }

    private Entry increase(Account target) {
        return new Entry(target, amount, target.normalBalance());
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Hold amount must be positive, got %s %s".formatted(
                    amount.amount().toPlainString(),
                    amount.currency().getCurrencyCode()));
        }
    }

    private static void requireAccountCurrency(HoldableAccount account, Money amount) {
        if (!account.available().currency().equals(amount.currency())) {
            throw new CurrencyMismatchException(account.available().currency(), amount.currency());
        }
    }
}
