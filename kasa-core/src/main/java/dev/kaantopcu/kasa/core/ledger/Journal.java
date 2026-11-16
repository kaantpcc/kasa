package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Not thread-safe on its own: InMemoryLedger calls it only while holding its read or write lock.
final class Journal {

    private final Map<IdempotencyKey, PostedTransaction> postedByKey = new HashMap<>();
    private final List<PostedTransaction> postedInOrder = new ArrayList<>();

    void append(PostedTransaction posted) {
        postedByKey.put(posted.transaction().idempotencyKey(), posted);
        postedInOrder.add(posted);
    }

    Optional<PostedTransaction> postedWith(IdempotencyKey key) {
        return Optional.ofNullable(postedByKey.get(key));
    }

    Optional<Instant> latestRecordedAt() {
        if (postedInOrder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(postedInOrder.getLast().recordedAt());
    }

    Money balanceOf(Account account) {
        Money balance = Money.zero(account.currency());
        for (Entry entry : entriesOf(account)) {
            if (entry.account().equals(account)) {
                balance = balance.add(entry.balanceChange());
            }
        }
        return balance;
    }

    List<Entry> entriesOf(Account account) {
        List<Entry> accountEntries = new ArrayList<>();
        for (PostedTransaction posted : postedInOrder) {
            accountEntries.addAll(posted.transaction().entriesFor(account));
        }
        return List.copyOf(accountEntries);
    }
}
