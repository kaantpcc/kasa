package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Not thread-safe on its own: InMemoryLedger calls it only while holding its read or write lock.
final class Journal {

    private final Map<IdempotencyKey, PostedTransaction> postedByKey = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();

    void append(PostedTransaction posted) {
        postedByKey.put(posted.transaction().idempotencyKey(), posted);
        entries.addAll(posted.transaction().entries());
    }

    Optional<PostedTransaction> postedWith(IdempotencyKey key) {
        return Optional.ofNullable(postedByKey.get(key));
    }

    Money balanceOf(Account account) {
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(entry.balanceChange());
            }
        }
        return balance;
    }

    List<Entry> entriesOf(Account account) {
        List<Entry> accountEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                accountEntries.add(entry);
            }
        }
        return List.copyOf(accountEntries);
    }
}
