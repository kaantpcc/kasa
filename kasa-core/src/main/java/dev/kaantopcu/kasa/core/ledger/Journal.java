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

    List<PostedTransaction> refundsOf(IdempotencyKey originalKey) {
        List<PostedTransaction> refunds = new ArrayList<>();
        for (PostedTransaction posted : postedInOrder) {
            if (posted.transaction().isRefundOf(originalKey)) {
                refunds.add(posted);
            }
        }
        return List.copyOf(refunds);
    }

    Optional<Instant> latestRecordedAt() {
        if (postedInOrder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(postedInOrder.getLast().recordedAt());
    }

    Money balanceOf(Account account) {
        return sumOf(account, entriesOf(account));
    }

    Money balanceBefore(Account account, Instant before) {
        return sumOf(account, entriesBefore(account, before));
    }

    List<Entry> entriesOf(Account account) {
        List<Entry> accountEntries = new ArrayList<>();
        for (PostedTransaction posted : postedInOrder) {
            accountEntries.addAll(posted.transaction().entriesFor(account));
        }
        return List.copyOf(accountEntries);
    }

    List<Entry> entriesBefore(Account account, Instant before) {
        List<Entry> accountEntries = new ArrayList<>();
        for (PostedTransaction posted : postedInOrder) {
            if (!posted.recordedAt().isBefore(before)) {
                break;
            }
            accountEntries.addAll(posted.transaction().entriesFor(account));
        }
        return List.copyOf(accountEntries);
    }

    List<PostedTransaction> postedBetween(Instant from, Instant to) {
        List<PostedTransaction> between = new ArrayList<>();
        for (PostedTransaction posted : postedInOrder) {
            if (!posted.recordedAt().isBefore(to)) {
                break;
            }
            if (!posted.recordedAt().isBefore(from)) {
                between.add(posted);
            }
        }
        return List.copyOf(between);
    }

    private static Money sumOf(Account account, List<Entry> entries) {
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            balance = balance.add(entry.balanceChange());
        }
        return balance;
    }
}
