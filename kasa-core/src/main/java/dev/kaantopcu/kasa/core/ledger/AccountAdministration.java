package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;

public interface AccountAdministration {

    void open(Account account);

    void freeze(Account account);

    void unfreeze(Account account);

    void close(Account account);
}
