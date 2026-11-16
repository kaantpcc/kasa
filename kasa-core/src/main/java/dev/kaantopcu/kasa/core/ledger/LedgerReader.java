package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.List;
import java.util.Optional;

public interface LedgerReader {

    Money balance(Account account);

    List<Entry> statement(Account account);

    Money totalBalance(HoldableAccount account);

    Money balanceUnder(AccountId branch);

    AccountStatus status(Account account);

    Optional<PostedTransaction> posted(IdempotencyKey key);
}
