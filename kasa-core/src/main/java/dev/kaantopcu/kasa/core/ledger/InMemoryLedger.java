package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Ledger} that keeps everything in memory.
 *
 * <p>Use it in tests, prototypes, or applications that rebuild their state on start-up; everything is lost
 * when the process stops.
 *
 * <p><b>Thread safety:</b> an instance is safe to share between threads. Writes run one at a time, and
 * reads run in parallel with each other but never during a write, so a reader never sees half of a
 * transaction. Every returned object is immutable.
 *
 * <p><b>Time:</b> the ledger stamps each transaction with the {@link Clock} it was created with. Pass
 * {@code Clock.systemUTC()} in production and a fixed clock in tests. If the clock moves backwards, the
 * ledger reuses the last recorded time, so recording times never go backwards. A transaction without a
 * value date gets the recording date in the clock's time zone.
 *
 * <p><b>Performance:</b> posting, current balances, active holds and branch balances do not slow down as the
 * ledger grows. Past balances and statements find their period by binary search over the account's entries,
 * then copy only the lines in it. {@link #verifyChain()} and {@link #trialBalance(Instant)} read every
 * transaction.
 */
public final class InMemoryLedger implements Ledger {

    private final AccountRegistry accounts = new AccountRegistry();
    private final Journal journal = new Journal();
    private final HoldBook holds = new HoldBook();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Clock clock;

    /**
     * Creates an empty ledger.
     *
     * @param clock the clock that stamps recording times and decides default value dates
     */
    public InMemoryLedger(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void open(Account account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.open(account));
    }

    @Override
    public void openHoldable(HoldableAccount account) {
        Objects.requireNonNull(account, "account");
        write(() -> accounts.openHoldable(account));
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
        transaction.idempotencyKey().requireNotReserved();
        write(() -> recordOutsideHolds(transaction));
    }

    @Override
    public void placeHold(Hold hold) {
        Objects.requireNonNull(hold, "hold");
        hold.key().requireNotReserved();
        write(() -> recordHold(hold));
    }

    @Override
    public void captureHold(IdempotencyKey holdKey, Account destination, Money amount) {
        Objects.requireNonNull(holdKey, "holdKey");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(amount, "amount");
        write(() -> recordCapture(holdKey, destination, amount));
    }

    @Override
    public void releaseHold(IdempotencyKey holdKey) {
        Objects.requireNonNull(holdKey, "holdKey");
        write(() -> settleHold(holdKey, Hold::release));
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
    public List<Hold> activeHolds(HoldableAccount account) {
        Objects.requireNonNull(account, "account");
        return read(() -> activeHoldsOn(account));
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

    @Override
    public Optional<IdempotencyKey> verifyChain() {
        return read(() -> HashChain.firstBrokenLink(journal.postedInOrder()));
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

    private void recordOutsideHolds(Transaction transaction) {
        for (Entry entry : transaction.entries()) {
            accounts.requireNotHeld(entry.account());
        }
        record(transaction);
    }

    private void recordReversal(IdempotencyKey key) {
        PostedTransaction original = journal.postedWith(key)
                .orElseThrow(() -> new TransactionNotFoundException(key));
        recordOutsideHolds(original.transaction().reversedOn(original.valueDate()));
    }

    private void recordHold(Hold hold) {
        record(hold.place());
        holds.remember(hold);
        accounts.markHeld(hold.account().held());
    }

    private void recordCapture(IdempotencyKey holdKey, Account destination, Money amount) {
        accounts.requireNotHeld(destination);
        settleHold(holdKey, hold -> hold.capture(destination, amount));
    }

    private void settleHold(IdempotencyKey holdKey, Function<Hold, Transaction> settlement) {
        Hold hold = holds.find(holdKey).orElseThrow(() -> new TransactionNotFoundException(holdKey));
        Transaction settlementTransaction = settlement.apply(hold);
        Optional<PostedTransaction> existing = journal.postedWith(settlementTransaction.idempotencyKey());
        if (existing.isPresent() && existing.get().transaction().equals(settlementTransaction)) {
            return;
        }
        if (!holds.isActive(hold)) {
            throw new HoldAlreadySettledException(holdKey);
        }
        record(settlementTransaction);
        holds.settle(hold);
    }

    private List<Hold> activeHoldsOn(HoldableAccount account) {
        accounts.requireOpened(account.available());
        return holds.activeOn(account);
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
        LocalDate valueDate = transaction.valueDate().orElse(LocalDate.ofInstant(recordedAt, clock.getZone()));
        Hash previousHash = journal.latestHash();
        Hash hash = HashChain.hashOf(previousHash, HashChain.canonicalOf(transaction, recordedAt, valueDate));
        return new PostedTransaction(transaction, recordedAt, valueDate, previousHash, hash);
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
        List<StatementLine> lines = journal.statementLines(account, from, to);
        Money closingBalance = lines.isEmpty() ? openingBalance : lines.getLast().balanceAfter();
        return new Statement(openingBalance, lines, closingBalance);
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
