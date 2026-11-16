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

public record Transaction(IdempotencyKey idempotencyKey, List<Entry> entries, Optional<LocalDate> valueDate,
                          Optional<String> description) {

    private static final String REVERSAL_SUFFIX = ":reversal";

    public Transaction {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(valueDate, "valueDate");
        Objects.requireNonNull(description, "description");
        entries = List.copyOf(entries);
        requireEntries(entries);
        requireBalancedPerCurrency(entries);
        description.ifPresent(Transaction::requireNonBlankDescription);
    }

    public Transaction(IdempotencyKey idempotencyKey, List<Entry> entries) {
        this(idempotencyKey, entries, Optional.empty(), Optional.empty());
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

    private static Money signedAmount(Entry entry) {
        return switch (entry.side()) {
            case DEBIT -> entry.amount();
            case CREDIT -> entry.amount().negate();
        };
    }

    public Transaction reversed() {
        List<Entry> reversedEntries = new ArrayList<>();
        for (Entry entry : entries) {
            reversedEntries.add(entry.reversed());
        }
        return new Transaction(new IdempotencyKey(idempotencyKey.value() + REVERSAL_SUFFIX), reversedEntries);
    }

    public static Builder builder(String idempotencyKey) {
        return new Builder(new IdempotencyKey(idempotencyKey));
    }

    public static final class Builder {
        private final IdempotencyKey idempotencyKey;
        private final List<Entry> entries = new ArrayList<>();
        private LocalDate valueDate;
        private String description;

        private Builder(IdempotencyKey idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }

        public Builder debit(Account account, Money amount) {
            entries.add(new Entry(account, amount, Side.DEBIT));
            return this;
        }

        public Builder credit(Account account, Money amount) {
            entries.add(new Entry(account, amount, Side.CREDIT));
            return this;
        }

        public Builder valueDate(LocalDate valueDate) {
            this.valueDate = Objects.requireNonNull(valueDate, "valueDate");
            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Transaction build() {
            return new Transaction(idempotencyKey, entries, Optional.ofNullable(valueDate), Optional.ofNullable(description));
        }
    }
}
