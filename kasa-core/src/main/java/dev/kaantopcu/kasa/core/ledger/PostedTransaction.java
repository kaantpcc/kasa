package dev.kaantopcu.kasa.core.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A transaction as the ledger recorded it, with its time, value date and place in the hash chain.
 *
 * <p>Instances are created by the ledger; read them with {@link LedgerReader#posted(IdempotencyKey)} or
 * from a {@link StatementLine}.
 *
 * @param transaction  the recorded transaction
 * @param recordedAt   when the ledger recorded it; never earlier than the transaction recorded before it
 * @param valueDate    the date the money counts from; the transaction's own value date, or the recording
 *                     date in the ledger clock's time zone
 * @param previousHash the hash of the transaction recorded before it, or {@link Hash#GENESIS} for the first
 * @param hash         the hash of this transaction's content and {@code previousHash}
 */
public record PostedTransaction(Transaction transaction,
                                Instant recordedAt,
                                LocalDate valueDate,
                                Hash previousHash,
                                Hash hash) {

    /**
     * Creates a posted transaction.
     *
     * @param transaction  the recorded transaction
     * @param recordedAt   when the ledger recorded it
     * @param valueDate    the date the money counts from
     * @param previousHash the hash of the transaction recorded before it
     * @param hash         the hash of this transaction's content and {@code previousHash}
     */
    public PostedTransaction {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(valueDate, "valueDate");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(hash, "hash");
    }
}
