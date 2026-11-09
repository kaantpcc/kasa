package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
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

    private final AccountRegistry accounts = new AccountRegistry();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<IdempotencyKey, Transaction> transactionsByKey = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void open(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.open(account));
    }

    @Override
    public void freeze(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.freeze(account));
    }

    @Override
    public void unfreeze(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.unfreeze(account));
    }

    @Override
    public void close(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.close(account, calculateBalance(account)));
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
    public Money balanceUnder(AccountId branch) {
        Objects.requireNonNull(branch, "branch");
        return read(() -> calculateBranchBalance(branch));
    }

    @Override
    public AccountStatus status(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> accounts.statusOf(account));
    }

    private void record(Transaction transaction) {
        if (transactionsByKey.containsKey(transaction.idempotencyKey())) {
            requireSameTransaction(transactionsByKey.get(transaction.idempotencyKey()), transaction);
            return;
        }
        requireOpenedAccounts(transaction);
        Map<Account, Money> changes = balanceChanges(transaction);
        changes.forEach(accounts::requireMovementAllowed);
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
        accounts.requireOpened(account);
        Money balance = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.account().equals(account)) {
                balance = balance.add(signedAmount(entry, account.normalBalance()));
            }
        }
        return balance;
    }

    private Money calculateBranchBalance(AccountId branch) {
        List<Account> branchAccounts = accounts.accountsWithin(branch);
        requireSummable(branch, branchAccounts);
        Money total = Money.zero(branchAccounts.getFirst().currency());
        for (Account account : branchAccounts) {
            total = total.add(calculateBalance(account));
        }
        return total;
    }

    private List<Entry> entriesOf(Account account) {
        accounts.requireOpened(account);
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
            accounts.requireOpened(entry.account());
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

    private static void requireSummable(AccountId branch, List<Account> branchAccounts) {
        Account first = branchAccounts.getFirst();
        for (Account account : branchAccounts) {
            if (!account.currency().equals(first.currency())) {
                throw new CurrencyMismatchException(first.currency(), account.currency());
            }
            if (account.normalBalance() != first.normalBalance()) {
                throw new MixedNormalBalanceException(branch);
            }
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
