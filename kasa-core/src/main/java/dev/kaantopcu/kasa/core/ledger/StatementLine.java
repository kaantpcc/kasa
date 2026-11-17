package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record StatementLine(PostedTransaction posted, Entry entry, Money balanceAfter) {

    public StatementLine {
        Objects.requireNonNull(posted, "posted");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }

    public IdempotencyKey idempotencyKey() {
        return posted.transaction().idempotencyKey();
    }

    public Instant recordedAt() {
        return posted.recordedAt();
    }

    public LocalDate valueDate() {
        return posted.valueDate();
    }

    public Optional<String> description() {
        return posted.transaction().description();
    }
}
