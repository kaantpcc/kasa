package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A balanced set of entries that moves money between accounts in one step.
 *
 * <p>In every currency, the debit total equals the credit total. A transaction is only a request; it
 * changes balances once {@link LedgerWriter#post(Transaction)} records it. Build one with
 * {@link #builder(String)}:
 *
 * <pre>{@code
 * Transaction sale = Transaction.builder("order-1")
 *         .debit(customerPayments, Money.of("100.00", "TRY"))
 *         .credit(sellerWallet, Money.of("100.00", "TRY"))
 *         .description("Order 1 payment")
 *         .build();
 * }</pre>
 *
 * @param idempotencyKey the caller's unique key for the transaction
 * @param entries        the entries, at least one; balanced in every currency
 * @param valueDate      the date the money counts from; if empty, the ledger uses the recording date
 * @param description    a human-readable description; if present, not blank
 * @param refundOf       the key of the transaction this one refunds; the ledger then limits it to what the
 *                       original moved
 */
public record Transaction(IdempotencyKey idempotencyKey,
                          List<Entry> entries,
                          Optional<LocalDate> valueDate,
                          Optional<String> description,
                          Optional<IdempotencyKey> refundOf) {

    /**
     * Creates a transaction.
     *
     * @param idempotencyKey the caller's unique key for the transaction
     * @param entries        the entries, at least one; copied
     * @param valueDate      the date the money counts from, or empty for the recording date
     * @param description    a human-readable description, or empty
     * @param refundOf       the key of the transaction this one refunds, or empty
     * @throws IllegalArgumentException       if there are no entries, the description is blank, or the
     *                                        transaction refunds itself
     * @throws UnbalancedTransactionException if debits and credits differ in some currency
     */
    public Transaction {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(valueDate, "valueDate");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(refundOf, "refundOf");
        entries = List.copyOf(entries);
        requireEntries(entries);
        requireBalancedPerCurrency(entries);
        description.ifPresent(Transaction::requireNonBlankDescription);
        refundOf.ifPresent(original -> requireNotRefundingItself(idempotencyKey, original));
    }

    /**
     * Creates a transaction with no value date, description or refund link.
     *
     * @param idempotencyKey the caller's unique key for the transaction
     * @param entries        the entries, at least one; copied
     * @throws IllegalArgumentException       if there are no entries
     * @throws UnbalancedTransactionException if debits and credits differ in some currency
     */
    public Transaction(IdempotencyKey idempotencyKey, List<Entry> entries) {
        this(idempotencyKey, entries, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static void requireEntries(List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Transaction must have at least one entry");
        }
    }

    private static void requireBalancedPerCurrency(List<Entry> entries) {
        Map<Currency, Money> netByCurrency = new HashMap<>();
        for (Entry entry : entries) {
            Currency currency = entry.amount().currency();
            Money net = netByCurrency.getOrDefault(currency, Money.zero(currency));
            netByCurrency.put(currency, net.add(signedAmount(entry)));
        }
        for (Money net : netByCurrency.values()) {
            if (!net.isZero()) {
                throw new UnbalancedTransactionException(net);
            }
        }
    }

    private static void requireNonBlankDescription(String description) {
        if (description.isBlank()) {
            throw new IllegalArgumentException("Transaction description must not be blank");
        }
    }

    private static void requireNotRefundingItself(IdempotencyKey key, IdempotencyKey original) {
        if (key.equals(original)) {
            throw new IllegalArgumentException("Transaction %s cannot refund itself".formatted(key.value()));
        }
    }

    private static Money signedAmount(Entry entry) {
        return switch (entry.side()) {
            case DEBIT -> entry.amount();
            case CREDIT -> entry.amount().negate();
        };
    }

    Transaction reversedOn(LocalDate valueDate) {
        Objects.requireNonNull(valueDate, "valueDate");
        List<Entry> reversedEntries = new ArrayList<>();
        for (Entry entry : entries) {
            reversedEntries.add(entry.reversed());
        }
        return new Transaction(idempotencyKey.withSuffix(IdempotencyKey.REVERSAL_SUFFIX), reversedEntries,
                Optional.of(valueDate), Optional.empty(), Optional.of(idempotencyKey));
    }

    /**
     * Starts building a transaction.
     *
     * @param idempotencyKey the caller's unique key for the transaction, such as an order id
     * @return a new builder
     * @throws IllegalArgumentException if {@code idempotencyKey} is blank
     */
    public static Builder builder(String idempotencyKey) {
        return new Builder(new IdempotencyKey(idempotencyKey));
    }

    /**
     * Collects the entries and details of a transaction.
     *
     * <p>Each entry is checked when it is added; the balance of the whole transaction is checked by
     * {@link #build()}. A builder is not thread-safe.
     */
    public static final class Builder {
        private final IdempotencyKey idempotencyKey;
        private final List<Entry> entries = new ArrayList<>();
        private LocalDate valueDate;
        private String description;
        private IdempotencyKey refundOf;

        private Builder(IdempotencyKey idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }

        /**
         * Adds a debit entry.
         *
         * @param account the account to debit
         * @param amount  the positive amount, in the account's currency
         * @return this builder
         * @throws InvalidEntryAmountException if {@code amount} is zero or negative
         * @throws dev.kaantopcu.kasa.core.money.CurrencyMismatchException if {@code amount} is not in the
         *                                     account's currency
         */
        public Builder debit(Account account, Money amount) {
            entries.add(new Entry(account, amount, Side.DEBIT));
            return this;
        }

        /**
         * Adds a credit entry.
         *
         * @param account the account to credit
         * @param amount  the positive amount, in the account's currency
         * @return this builder
         * @throws InvalidEntryAmountException if {@code amount} is zero or negative
         * @throws dev.kaantopcu.kasa.core.money.CurrencyMismatchException if {@code amount} is not in the
         *                                     account's currency
         */
        public Builder credit(Account account, Money amount) {
            entries.add(new Entry(account, amount, Side.CREDIT));
            return this;
        }

        /**
         * Sets the date the money counts from. Without it, the ledger uses the recording date.
         *
         * @param valueDate the value date
         * @return this builder
         */
        public Builder valueDate(LocalDate valueDate) {
            this.valueDate = Objects.requireNonNull(valueDate, "valueDate");
            return this;
        }

        /**
         * Sets a human-readable description.
         *
         * @param description the description; must not be blank when the transaction is built
         * @return this builder
         */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /**
         * Marks the transaction as a refund of an earlier one.
         *
         * <p>When it is posted, the ledger rejects it if, together with earlier refunds of the same
         * original, it would give back more to any account than the original moved. Earlier refunds count
         * net of anything that later gave them back, such as their reversals. Partial refunds are allowed.
         *
         * @param originalKey the idempotency key of the transaction being refunded
         * @return this builder
         * @throws IllegalArgumentException if {@code originalKey} is blank
         */
        public Builder refundOf(String originalKey) {
            this.refundOf = new IdempotencyKey(originalKey);
            return this;
        }

        /**
         * Builds the transaction.
         *
         * @return the transaction
         * @throws IllegalArgumentException       if no entries were added, the description is blank, or the
         *                                        transaction refunds itself
         * @throws UnbalancedTransactionException if debits and credits differ in some currency
         */
        public Transaction build() {
            return new Transaction(idempotencyKey, entries,
                    Optional.ofNullable(valueDate), Optional.ofNullable(description),
                    Optional.ofNullable(refundOf));
        }
    }
}
