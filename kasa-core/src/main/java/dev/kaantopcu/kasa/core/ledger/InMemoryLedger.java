package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InMemoryLedger {

    private final List<Entry> entries = new ArrayList<>();

    public void post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        entries.addAll(transaction.entries());
    }

    public Money balance(Account account) {
        Objects.requireNonNull(account, "account");
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(signedAmount(entry, account.normalBalance()));
            }
        }
        return balance;
    }

    public List<Entry> statement(Account account) {
        Objects.requireNonNull(account, "account");
        List<Entry> accountEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                accountEntries.add(entry);
            }
        }
        return List.copyOf(accountEntries);
    }

    private static Money signedAmount(Entry entry, Side normalBalance) {
        if (entry.side() == normalBalance) {
            return entry.amount();
        }
        return entry.amount().negate();
    }
}
