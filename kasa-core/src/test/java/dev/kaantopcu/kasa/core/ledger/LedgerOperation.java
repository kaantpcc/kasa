package dev.kaantopcu.kasa.core.ledger;

import java.util.Objects;
import java.util.function.Consumer;

record LedgerOperation(String description, Consumer<Ledger> action) {

    LedgerOperation {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(action, "action");
    }

    void applyTo(Ledger ledger) {
        action.accept(ledger);
    }
}
