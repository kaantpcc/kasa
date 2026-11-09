package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryLedger implements LedgerWriter, LedgerReader {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<IdempotencyKey, Transaction> transactionsByKey = new HashMap<>();

    @Override
    public synchronized void post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        record(transaction);
    }

    @Override
    public synchronized void placeHold(Hold hold) {
        Objects.requireNonNull(hold, "hold");
        if (!transactionsByKey.containsKey(hold.key())) {
            requireSufficientFunds(hold);
        }
        record(hold.place());
    }

    @Override
    public synchronized void reverse(IdempotencyKey key) {
        Objects.requireNonNull(key, "key");
        if (!transactionsByKey.containsKey(key)) {
            throw new TransactionNotFoundException(key);
        }
        record(transactionsByKey.get(key).reversed());
    }

    @Override
    public synchronized Money balance(Account account) {
        Objects.requireNonNull(account, "account");
        return calculateBalance(account);
    }

    @Override
    public synchronized List<Entry> statement(Account account) {
        Objects.requireNonNull(account, "account");
        return entriesOf(account);
    }

    @Override
    public synchronized Money totalBalance(HoldableAccount account) {
        Objects.requireNonNull(account, "account");
        return calculateBalance(account.available()).add(calculateBalance(account.held()));
    }

    private void record(Transaction transaction) {
        if (transactionsByKey.containsKey(transaction.idempotencyKey())) {
            requireSameTransaction(transactionsByKey.get(transaction.idempotencyKey()), transaction);
            return;
        }
        transactionsByKey.put(transaction.idempotencyKey(), transaction);
        entries.addAll(transaction.entries());
    }

    private Money calculateBalance(Account account) {
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(signedAmount(entry, account.normalBalance()));
            }
        }
        return balance;
    }

    private List<Entry> entriesOf(Account account) {
        List<Entry> accountEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                accountEntries.add(entry);
            }
        }
        return List.copyOf(accountEntries);
    }

    private void requireSufficientFunds(Hold hold) {
        Account available = hold.account().available();
        Money availableBalance = calculateBalance(available);
        if (availableBalance.subtract(hold.amount()).isNegative()) {
            throw new InsufficientFundsException(available.id(), availableBalance, hold.amount());
        }
    }

    private static void requireSameTransaction(Transaction existing, Transaction incoming) {
        if (!existing.equals(incoming)) {
            throw new IdempotencyKeyConflictException(incoming.idempotencyKey());
        }
    }

    private static Money signedAmount(Entry entry, Side normalBalance) {
        if (entry.side() == normalBalance) {
            return entry.amount();
        }
        return entry.amount().negate();
    }
}