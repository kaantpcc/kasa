package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.HoldableAccount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Not thread-safe on its own: InMemoryLedger calls it only while holding its read or write lock.
final class HoldBook {

    private final Map<IdempotencyKey, Hold> holdsByKey = new LinkedHashMap<>();

    void remember(Hold hold) {
        holdsByKey.put(hold.key(), hold);
    }

    Optional<Hold> find(IdempotencyKey holdKey) {
        return Optional.ofNullable(holdsByKey.get(holdKey));
    }

    List<Hold> placedOn(HoldableAccount account) {
        List<Hold> placed = new ArrayList<>();
        for (Hold hold : holdsByKey.values()) {
            if (hold.account().equals(account)) {
                placed.add(hold);
            }
        }
        return placed;
    }
}
