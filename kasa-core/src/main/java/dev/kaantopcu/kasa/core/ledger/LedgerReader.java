package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.Money;
import dev.kaantopcu.kasa.core.account.HoldableAccount;

import java.util.List;

public interface LedgerReader {

    Money balance(Account account);

    List<Entry> statement(Account account);

    Money totalBalance(HoldableAccount account);
}
