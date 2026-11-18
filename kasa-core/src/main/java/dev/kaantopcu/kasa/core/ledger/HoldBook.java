package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.HoldableAccount;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Not thread-safe on its own: InMemoryLedger calls it only while holding its read or write lock.
// Active holds are kept per account so that listing them does not scan every hold ever placed (ADR-0019).
final class HoldBook {

    private final Map<IdempotencyKey, Hold> holdsByKey = new HashMap<>();
    private final Map<HoldableAccount, Map<IdempotencyKey, Hold>> activeByAccount = new HashMap<>();

    void remember(Hold hold) {
        if (holdsByKey.putIfAbsent(hold.key(), hold) == null) {
            activeByAccount.computeIfAbsent(hold.account(), account -> new LinkedHashMap<>()).put(hold.key(), hold);
        }
    }

    void settle(Hold hold) {
        Map<IdempotencyKey, Hold> active = activeByAccount.get(hold.account());
        active.remove(hold.key());
    }

    Optional<Hold> find(IdempotencyKey holdKey) {
        return Optional.ofNullable(holdsByKey.get(holdKey));
    }

    boolean isActive(Hold hold) {
        return activeByAccount.getOrDefault(hold.account(), Map.of()).containsKey(hold.key());
    }

    List<Hold> activeOn(HoldableAccount account) {
        return List.copyOf(activeByAccount.getOrDefault(account, Map.of()).values());
    }
}
