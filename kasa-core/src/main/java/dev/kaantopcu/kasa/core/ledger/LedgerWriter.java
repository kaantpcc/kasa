package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;

public interface LedgerWriter {

    void open(Account account);

    void freeze(Account account);

    void unfreeze(Account account);

    void close(Account account);

    void post(Transaction transaction);

    void placeHold(Hold hold);

    void reverse(IdempotencyKey key);
}
