package dev.kaantopcu.kasa.core.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record PostedTransaction(Transaction transaction, Instant recordedAt, LocalDate valueDate) {

    public PostedTransaction {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(valueDate, "valueDate");
    }
}
