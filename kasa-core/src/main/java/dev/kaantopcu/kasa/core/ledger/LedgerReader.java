package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.List;

public interface LedgerReader {

    Money balance(Account account);

    List<Entry> statement(Account account);
}
