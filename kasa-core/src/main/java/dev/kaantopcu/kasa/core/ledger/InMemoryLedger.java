package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.*;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class InMemoryLedger implements Ledger {

    private final AccountRegistry accounts = new AccountRegistry();
    private final Journal journal = new Journal();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Clock clock;

    public InMemoryLedger(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

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
    public Money balanceAsOf(Account account, Instant asOf) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(asOf, "asOf");
        return read(() -> calculateBalanceAsOf(account, asOf));
    }

    @Override
    public List<Entry> entries(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> entriesOf(account));
    }

    @Override
    public Statement statement(Account account, Instant from, Instant to) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return read(() -> buildStatement(account, from, to));
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
    public TrialBalance trialBalance(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf");
        return read(() -> buildTrialBalance(asOf));
    }

    @Override
    public AccountStatus status(Account account) {
        Objects.requireNonNull(account, "account");
        return read(() -> accounts.statusOf(account));
    }

    @Override
    public Optional<PostedTransaction> posted(IdempotencyKey key) {
        Objects.requireNonNull(key, "key");
        return read(() -> journal.postedWith(key));
    }

    private void record(Transaction transaction) {
        Optional<PostedTransaction> existing = journal.postedWith(transaction.idempotencyKey());
        if (existing.isPresent()) {
            requireSameTransaction(existing.get().transaction(), transaction);
            return;
        }
        requireOpenedAccounts(transaction);
        Map<Account, Money> changes = balanceChanges(transaction);
        transaction.refundOf().ifPresent(originalKey -> requireWithinRefundable(originalKey, changes));
        changes.forEach(accounts::requireMovementAllowed);
        changes.forEach(this::requireWithinPolicy);
        journal.append(stamp(transaction));
    }

    private void recordReversal(IdempotencyKey key) {
        PostedTransaction original = journal.postedWith(key)
                .orElseThrow(() -> new TransactionNotFoundException(key));
        record(original.transaction().reversedOn(original.valueDate()));
    }

    private void requireWithinRefundable(IdempotencyKey originalKey, Map<Account, Money> changes) {
        RefundLimits limits = refundLimitsOf(originalKey);
        changes.forEach(limits::requireRefundable);
    }

    private RefundLimits refundLimitsOf(IdempotencyKey originalKey) {
        PostedTransaction original = journal.postedWith(originalKey)
                .orElseThrow(() -> new TransactionNotFoundException(originalKey));
        Map<Account, Money> refundedChanges = new LinkedHashMap<>();
        for (PostedTransaction refund : journal.refundsOf(originalKey)) {
            balanceChanges(refund.transaction())
                    .forEach((account, change) -> refundedChanges.merge(account, change, Money::add));
        }
        return new RefundLimits(balanceChanges(original.transaction()), refundedChanges);
    }

    private PostedTransaction stamp(Transaction transaction) {
        Instant recordedAt = nextRecordedAt();
        LocalDate recordingDay = LocalDate.ofInstant(recordedAt, clock.getZone());
        return new PostedTransaction(transaction, recordedAt, transaction.valueDate().orElse(recordingDay));
    }

    private Instant nextRecordedAt() {
        Instant now = clock.instant();
        Optional<Instant> latest = journal.latestRecordedAt();
        if (latest.isPresent() && now.isBefore(latest.get())) {
            return latest.get();
        }
        return now;
    }

    private Money calculateBalance(Account account) {
        accounts.requireOpened(account);
        return journal.balanceOf(account);
    }

    private Money calculateBalanceAsOf(Account account, Instant asOf) {
        accounts.requireOpened(account);
        return journal.balanceBefore(account, asOf);
    }

    private List<Entry> entriesOf(Account account) {
        accounts.requireOpened(account);
        return journal.entriesOf(account);
    }

    private Statement buildStatement(Account account, Instant from, Instant to) {
        accounts.requireOpened(account);
        requireOrderedPeriod(from, to);
        Money openingBalance = journal.balanceBefore(account, from);
        Money runningBalance = openingBalance;
        List<StatementLine> lines = new ArrayList<>();
        for (PostedTransaction posted : journal.postedBetween(from, to)) {
            for (Entry entry : posted.transaction().entriesFor(account)) {
                runningBalance = runningBalance.add(entry.balanceChange());
                lines.add(new StatementLine(posted, entry, runningBalance));
            }
        }
        return new Statement(openingBalance, lines, runningBalance);
    }

    private static void requireOrderedPeriod(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Statement period must not end before it starts, got %s to %s"
                    .formatted(from, to));
        }
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

    private TrialBalance buildTrialBalance(Instant asOf) {
        List<TrialBalanceLine> lines = new ArrayList<>();
        for (Account account : accounts.opened()) {
            lines.add(trialBalanceLine(account, asOf));
        }
        return new TrialBalance(lines);
    }

    private TrialBalanceLine trialBalanceLine(Account account, Instant asOf) {
        List<Entry> entries = journal.entriesBefore(account, asOf);
        return new TrialBalanceLine(account, totalOn(account, entries, Side.DEBIT), totalOn(account, entries, Side.CREDIT));
    }

    private static Money totalOn(Account account, List<Entry> entries, Side side) {
        Money total = Money.zero(account.currency());
        for (Entry entry : entries) {
            if (entry.side() == side) {
                total = total.add(entry.amount());
            }
        }
        return total;
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
            case BalancePolicy.NonNegative() ->
                    requireSpendable(account, balanceChange, Money.zero(account.currency()));
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
            changes.merge(entry.account(), entry.balanceChange(), Money::add);
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
}
