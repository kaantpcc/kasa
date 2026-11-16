package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Hold(IdempotencyKey key, HoldableAccount account, Money amount) {

    private static final String CAPTURE_SUFFIX = ":capture";
    private static final String RELEASE_SUFFIX = ":release";

    public Hold {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        requirePositive(amount);
        requireAccountCurrency(account, amount);
    }

    Transaction place() {
        return new Transaction(key, List.of(
                decrease(account.available()),
                increase(account.held(), amount)));
    }

    Transaction capture(Account destination, Money capturedAmount) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(capturedAmount, "capturedAmount");
        requireCapturable(capturedAmount);
        List<Entry> entries = new ArrayList<>();
        entries.add(decrease(account.held()));
        entries.add(new Entry(destination, capturedAmount, account.held().normalBalance()));
        Money remainder = amount.subtract(capturedAmount);
        if (remainder.isPositive()) {
            entries.add(increase(account.available(), remainder));
        }
        return new Transaction(suffixedKey(CAPTURE_SUFFIX), entries);
    }

    Transaction release() {
        return new Transaction(suffixedKey(RELEASE_SUFFIX), List.of(
                decrease(account.held()),
                increase(account.available(), amount)));
    }

    private Entry decrease(Account target) {
        return new Entry(target, amount, target.normalBalance().opposite());
    }

    private static Entry increase(Account target, Money value) {
        return new Entry(target, value, target.normalBalance());
    }

    private IdempotencyKey suffixedKey(String suffix) {
        return new IdempotencyKey(key.value() + suffix);
    }

    private void requireCapturable(Money capturedAmount) {
        if (!capturedAmount.isPositive() || amount.isLessThan(capturedAmount)) {
            throw new IllegalArgumentException("Captured amount must be positive and at most %s %s, got %s %s".formatted(
                    amount.amount().toPlainString(),
                    amount.currency().getCurrencyCode(),
                    capturedAmount.amount().toPlainString(),
                    capturedAmount.currency().getCurrencyCode()));
        }
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
