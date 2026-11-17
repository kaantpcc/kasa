package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

public interface LedgerWriter {

    void post(Transaction transaction);

    void placeHold(Hold hold);

    void captureHold(IdempotencyKey holdKey, Account destination, Money amount);

    default void captureHold(String holdKey, Account destination, Money amount) {
        Objects.requireNonNull(holdKey, "holdKey");
        captureHold(new IdempotencyKey(holdKey), destination, amount);
    }

    void releaseHold(IdempotencyKey holdKey);

    default void releaseHold(String holdKey) {
        Objects.requireNonNull(holdKey, "holdKey");
        releaseHold(new IdempotencyKey(holdKey));
    }

    void reverse(IdempotencyKey key);

    default void reverse(String key) {
        Objects.requireNonNull(key, "key");
        reverse(new IdempotencyKey(key));
    }
}
