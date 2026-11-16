package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

public interface LedgerWriter {

    void post(Transaction transaction);

    void placeHold(Hold hold);

    void captureHold(IdempotencyKey holdKey, Account destination, Money amount);

    void releaseHold(IdempotencyKey holdKey);

    void reverse(IdempotencyKey key);
}
