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
// The per-account histories and the refund index are derived from the posted transactions (ADR-0019);
// the transactions stay the only source of truth (ADR-0001).
final class Journal {

    private final Map<IdempotencyKey, PostedTransaction> postedByKey = new HashMap<>();
    private final List<PostedTransaction> postedInOrder = new ArrayList<>();
    private final Map<Account, AccountHistory> historiesByAccount = new HashMap<>();
    private final Map<IdempotencyKey, List<PostedTransaction>> refundsByOriginal = new HashMap<>();

    void append(PostedTransaction posted) {
        Transaction transaction = posted.transaction();
        postedByKey.put(transaction.idempotencyKey(), posted);
        postedInOrder.add(posted);
        transaction.refundOf().ifPresent(originalKey ->
                refundsByOriginal.computeIfAbsent(originalKey, key -> new ArrayList<>()).add(posted));
        for (Entry entry : transaction.entries()) {
            historiesByAccount.computeIfAbsent(entry.account(), AccountHistory::new).record(posted, entry);
        }
    }

    Optional<PostedTransaction> postedWith(IdempotencyKey key) {
        return Optional.ofNullable(postedByKey.get(key));
    }

    List<PostedTransaction> refundsOf(IdempotencyKey originalKey) {
        return List.copyOf(refundsByOriginal.getOrDefault(originalKey, List.of()));
    }

    Optional<Instant> latestRecordedAt() {
        if (postedInOrder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(postedInOrder.getLast().recordedAt());
    }

    Hash latestHash() {
        if (postedInOrder.isEmpty()) {
            return Hash.GENESIS;
        }
        return postedInOrder.getLast().hash();
    }

    List<PostedTransaction> postedInOrder() {
        return List.copyOf(postedInOrder);
    }

    Money balanceOf(Account account) {
        return historyOf(account).balance();
    }

    Money balanceBefore(Account account, Instant before) {
        return historyOf(account).balanceBefore(before);
    }

    List<Entry> entriesOf(Account account) {
        return historyOf(account).entries();
    }

    List<Entry> entriesBefore(Account account, Instant before) {
        return historyOf(account).entriesBefore(before);
    }

    List<StatementLine> statementLines(Account account, Instant from, Instant to) {
        return historyOf(account).linesBetween(from, to);
    }

    private AccountHistory historyOf(Account account) {
        AccountHistory history = historiesByAccount.get(account);
        if (history == null) {
            return new AccountHistory(account);
        }
        return history;
    }
}
