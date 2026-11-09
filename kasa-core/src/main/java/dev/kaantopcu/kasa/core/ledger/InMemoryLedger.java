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

import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryLedger implements LedgerWriter, LedgerReader {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<IdempotencyKey, Transaction> transactionsByKey = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        lock.writeLock().lock();
        try {
            record(transaction);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void placeHold(Hold hold) {
        Objects.requireNonNull(hold, "hold");
        lock.writeLock().lock();
        try {
            if (!transactionsByKey.containsKey(hold.key())) {
                requireSufficientFunds(hold);
            }
            record(hold.place());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void reverse(IdempotencyKey key) {
        Objects.requireNonNull(key, "key");
        lock.writeLock().lock();
        try {
            if (!transactionsByKey.containsKey(key)) {
                throw new TransactionNotFoundException(key);
            }
            record(transactionsByKey.get(key).reversed());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Money balance(Account account) {
        Objects.requireNonNull(account, "account");
        lock.readLock().lock();
        try {
            return calculateBalance(account);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Entry> statement(Account account) {
        Objects.requireNonNull(account, "account");
        lock.readLock().lock();
        try {
            return entriesOf(account);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Money totalBalance(HoldableAccount account) {
        Objects.requireNonNull(account, "account");
        lock.readLock().lock();
        try {
            return calculateBalance(account.available()).add(calculateBalance(account.held()));
        } finally {
            lock.readLock().unlock();
        }
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