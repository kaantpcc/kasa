package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

public record StatementLine(PostedTransaction posted, Entry entry, Money balanceAfter) {

    public StatementLine {
        Objects.requireNonNull(posted, "posted");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }
}
