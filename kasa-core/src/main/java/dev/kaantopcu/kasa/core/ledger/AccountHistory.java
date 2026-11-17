package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Not thread-safe on its own: Journal uses it only while InMemoryLedger holds its read or write lock.
final class AccountHistory {

    private final Money zero;
    private final List<StatementLine> lines = new ArrayList<>();

    AccountHistory(Account account) {
        this.zero = Money.zero(account.currency());
    }

    void record(PostedTransaction posted, Entry entry) {
        Money balanceAfter = balance().add(entry.balanceChange());
        lines.add(new StatementLine(posted, entry, balanceAfter));
    }

    Money balance() {
        return balanceAfterFirst(lines.size());
    }

    Money balanceBefore(Instant before) {
        return balanceAfterFirst(countBefore(before));
    }

    List<Entry> entries() {
        return entriesOf(lines);
    }

    List<Entry> entriesBefore(Instant before) {
        return entriesOf(lines.subList(0, countBefore(before)));
    }

    List<StatementLine> linesBetween(Instant from, Instant to) {
        return List.copyOf(lines.subList(countBefore(from), countBefore(to)));
    }

    private Money balanceAfterFirst(int count) {
        if (count == 0) {
            return zero;
        }
        return lines.get(count - 1).balanceAfter();
    }

    // Recorded times never go backwards (ADR-0012), so the lines are sorted and a binary search finds the boundary.
    private int countBefore(Instant before) {
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).posted().recordedAt().isBefore(before)) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static List<Entry> entriesOf(List<StatementLine> lines) {
        List<Entry> entries = new ArrayList<>(lines.size());
        for (StatementLine line : lines) {
            entries.add(line.entry());
        }
        return List.copyOf(entries);
    }
}
