package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class InMemoryLedger implements Ledger {

    private final Map<AccountId, Account> accountsById = new HashMap<>();
    private final Map<AccountId, AccountStatus> statusesById = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<IdempotencyKey, Transaction> transactionsByKey = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void open(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> recordAccount(account));
    }

    @Override
    public void freeze(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> changeStatus(account, AccountStatus.FROZEN));
    }

    @Override
    public void unfreeze(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> changeStatus(account, AccountStatus.OPEN));
    }

    @Override
    public void close(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> changeStatus(account, AccountStatus.CLOSED));
    }

    @Override
    public void post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        write(() -> record(transaction));
    }

    @Override
    public void placeHold(Hold hold) {
        Objects.requireNonNull(hold, "hold");
        write(() -> record(hold.place()));
    }

    @Override
    public void reverse(IdempotencyKey key) {
        Objects.requireNonNull(key, "key");
        write(() -> recordReversal(key));
    }

    @Override
    public Money balance(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> calculateBalance(account));
    }

    @Override
    public List<Entry> statement(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> entriesOf(account));
    }

    @Override
    public Money totalBalance(HoldableAccount account) {
        Objects.requireNonNull(account, "account");
        return read(() -> calculateBalance(account.available()).add(calculateBalance(account.held())));
    }

    @Override
    public AccountStatus status(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> statusOf(account));
    }

    private void recordAccount(Account account) {
        if (accountsById.containsKey(account.id())) {
            requireSameDefinition(account);
            return;
        }
        accountsById.put(account.id(), account);
        statusesById.put(account.id(), AccountStatus.OPEN);
    }

    private void changeStatus(Account account, AccountStatus target) {
        AccountStatus current = statusOf(account);
        if (current == target) {
            return;
        }
        if (current == AccountStatus.CLOSED) {
            throw new AccountClosedException(account.id());
        }
        statusesById.put(account.id(), target);
    }

    private AccountStatus statusOf(Account account) {
        requireOpened(account);
        return statusesById.get(account.id());
    }

    private void record(Transaction transaction) {
        if (transactionsByKey.containsKey(transaction.idempotencyKey())) {
            requireSameTransaction(transactionsByKey.get(transaction.idempotencyKey()), transaction);
            return;
        }
        requireOpenedAccounts(transaction);
        Map<Account, Money> changes = balanceChanges(transaction);
        changes.forEach(this::requireStatusAllows);
        changes.forEach(this::requireWithinPolicy);
        transactionsByKey.put(transaction.idempotencyKey(), transaction);
        entries.addAll(transaction.entries());
    }

    private void recordReversal(IdempotencyKey key) {
        if (!transactionsByKey.containsKey(key)) {
            throw new TransactionNotFoundException(key);
        }
        record(transactionsByKey.get(key).reversed());
    }

    private Money calculateBalance(Account account) {
        requireOpened(account);
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(signedAmount(entry, account.normalBalance()));
            }
        }
        return balance;
    }

    private List<Entry> entriesOf(Account account) {
        requireOpened(account);
        List<Entry> accountEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                accountEntries.add(entry);
            }
        }
        return List.copyOf(accountEntries);
    }

    private void requireOpenedAccounts(Transaction transaction) {
        for (Entry entry : transaction.entries()) {
            requireOpened(entry.account());
        }
    }

    private void requireOpened(Account account) {
        if (!accountsById.containsKey(account.id())) {
            throw new AccountNotOpenException(account.id());
        }
        requireSameDefinition(account);
    }

    private void requireSameDefinition(Account account) {
        if (!accountsById.get(account.id()).equals(account)) {
            throw new AccountDefinitionConflictException(account.id());
        }
    }

    private void requireStatusAllows(Account account, Money balanceChange) {
        if (statusesById.get(account.id()) == AccountStatus.FROZEN && balanceChange.isNegative()) {
            throw new AccountFrozenException(account.id());
        }
    }

    private void requireWithinPolicy(Account account, Money balanceChange) {
        switch (account.balancePolicy()) {
            case BalancePolicy.Unlimited() -> {
            }
            case BalancePolicy.NonNegative() -> requireSpendable(account, balanceChange, Money.zero(account.currency()));
            case BalancePolicy.Overdraft(Money limit) -> requireSpendable(account, balanceChange, limit);
        }
    }

    private void requireSpendable(Account account, Money balanceChange, Money overdraftLimit) {
        if (!balanceChange.isNegative()) {
            return;
        }
        Money spendable = calculateBalance(account).add(overdraftLimit);
        Money requested = balanceChange.negate();
        if (spendable.isLessThan(requested)) {
            throw new InsufficientFundsException(account.id(), spendable, requested);
        }
    }

    private static Map<Account, Money> balanceChanges(Transaction transaction) {
        Map<Account, Money> changes = new LinkedHashMap<>();
        for (Entry entry : transaction.entries()) {
            changes.merge(entry.account(), signedAmount(entry, entry.account().normalBalance()), Money::add);
        }
        return changes;
    }

    private static void requireSameTransaction(Transaction existing, Transaction incoming) {
        if (!existing.equals(incoming)) {
            throw new IdempotencyKeyConflictException(incoming.idempotencyKey());
        }
    }

    private <T> T read(Supplier<T> action) {
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void write(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static Money signedAmount(Entry entry, Side normalBalance) {
        if (entry.side() == normalBalance) {
            return entry.amount();
        }
        return entry.amount().negate();
    }
}