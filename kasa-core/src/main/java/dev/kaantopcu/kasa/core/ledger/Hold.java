package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A request to put an amount on hold in a holdable account, for example a card authorization.
 *
 * <p>{@link LedgerWriter#placeHold(Hold)} moves the amount from the available account to the held account.
 * The hold is then settled exactly once: {@link LedgerWriter#captureHold(IdempotencyKey, Account, Money)}
 * sends all or part of it to a destination and returns the rest, or
 * {@link LedgerWriter#releaseHold(IdempotencyKey)} returns all of it.
 *
 * @param key     the caller's unique key for the hold; capture and release refer to it
 * @param account the holdable account the money is held in
 * @param amount  the positive amount to hold, in the account's currency
 */
public record Hold(IdempotencyKey key, HoldableAccount account, Money amount) {

    /**
     * Creates a hold request.
     *
     * @param key     the caller's unique key for the hold
     * @param account the holdable account the money is held in
     * @param amount  the positive amount to hold, in the account's currency
     * @throws IllegalArgumentException  if {@code amount} is zero or negative
     * @throws CurrencyMismatchException if {@code amount} is not in the account's currency
     */
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
        return new Transaction(captureKey(), entries);
    }

    Transaction release() {
        return new Transaction(releaseKey(), List.of(
                decrease(account.held()),
                increase(account.available(), amount)));
    }

    IdempotencyKey captureKey() {
        return key.withSuffix(IdempotencyKey.CAPTURE_SUFFIX);
    }

    IdempotencyKey releaseKey() {
        return key.withSuffix(IdempotencyKey.RELEASE_SUFFIX);
    }

    private Entry decrease(Account target) {
        return new Entry(target, amount, target.normalBalance().opposite());
    }

    private static Entry increase(Account target, Money value) {
        return new Entry(target, value, target.normalBalance());
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
