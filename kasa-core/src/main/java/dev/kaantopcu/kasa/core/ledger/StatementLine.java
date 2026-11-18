package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One movement on an account statement, with the balance right after it.
 *
 * @param posted       the posted transaction the entry belongs to
 * @param entry        the entry that moved the account
 * @param balanceAfter the account's balance after this entry
 */
public record StatementLine(PostedTransaction posted, Entry entry, Money balanceAfter) {

    /**
     * Creates a statement line.
     *
     * @param posted       the posted transaction the entry belongs to
     * @param entry        the entry that moved the account
     * @param balanceAfter the account's balance after this entry
     */
    public StatementLine {
        Objects.requireNonNull(posted, "posted");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }

    /**
     * Returns the idempotency key of the transaction.
     *
     * @return the transaction's key
     */
    public IdempotencyKey idempotencyKey() {
        return posted.transaction().idempotencyKey();
    }

    /**
     * Returns when the ledger recorded the transaction.
     *
     * @return the recording time
     */
    public Instant recordedAt() {
        return posted.recordedAt();
    }

    /**
     * Returns the date the money counts from.
     *
     * @return the transaction's value date
     */
    public LocalDate valueDate() {
        return posted.valueDate();
    }

    /**
     * Returns the description of the transaction, if it has one.
     *
     * @return the description, or empty
     */
    public Optional<String> description() {
        return posted.transaction().description();
    }
}
