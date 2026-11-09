package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
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
    private final List<Entry> entries = new ArrayList<>();
    private final Map<IdempotencyKey, Transaction> transactionsByKey = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void open(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> recordAccount(account));
    }

    @Override
    public void post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        write(() -> record(transaction));
    }

    @Override
    public void placeHold(Hold hold) {
        Objects.requireNonNull(hold, "hold");
        write(() -> recordHold(hold));
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

    private void recordAccount(Account account) {
        if (accountsById.containsKey(account.id())) {
            requireSameDefinition(account);
            return;
        }
        accountsById.put(account.id(), account);
    }

    private void record(Transaction transaction) {
        if (transactionsByKey.containsKey(transaction.idempotencyKey())) {
            requireSameTransaction(transactionsByKey.get(transaction.idempotencyKey()), transaction);
            return;
        }
        requireOpenAccounts(transaction);
        requireBalancePolicies(transaction);
        transactionsByKey.put(transaction.idempotencyKey(), transaction);
        entries.addAll(transaction.entries());
    }

    private void recordHold(Hold hold) {
        if (!transactionsByKey.containsKey(hold.key())) {
            requireSufficientFunds(hold);
        }
        record(hold.place());
    }

    private void recordReversal(IdempotencyKey key) {
        if (!transactionsByKey.containsKey(key)) {
            throw new TransactionNotFoundException(key);
        }
        record(transactionsByKey.get(key).reversed());
    }

    private Money calculateBalance(Account account) {
        requireOpen(account);
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(signedAmount(entry, account.normalBalance()));
            }
        }
        return balance;
    }

    private List<Entry> entriesOf(Account account) {
        requireOpen(account);
        List<Entry> accountEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                accountEntries.add(entry);
            }
        }
        return List.copyOf(accountEntries);
    }

    private void requireOpenAccounts(Transaction transaction) {
        for (Entry entry : transaction.entries()) {
            requireOpen(entry.account());
        }
    }

    private void requireOpen(Account account) {
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

    private void requireBalancePolicies(Transaction transaction) {
        balanceChanges(transaction).forEach(this::requireWithinPolicy);
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

    private void requireSufficientFunds(Hold hold) {
        Account available = hold.account().available();
        Money availableBalance = calculateBalance(available);
        if (availableBalance.isLessThan(hold.amount())) {
            throw new InsufficientFundsException(available.id(), availableBalance, hold.amount());
        }
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