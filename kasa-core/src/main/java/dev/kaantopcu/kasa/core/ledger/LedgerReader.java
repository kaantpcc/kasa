package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads balances, entries, statements and the audit chain.
 *
 * <p>Balances are reported in each account's normal direction. Questions about the past use the time the
 * ledger <em>recorded</em> each transaction, which never goes backwards, so the answer for a past moment
 * never changes later. A transaction recorded exactly at {@code asOf} or {@code to} is not included. Value
 * dates are carried on each transaction but are not used to select them.
 *
 * <p>Methods that take an account throw {@link AccountNotOpenException} if it has not been opened, and
 * {@link AccountDefinitionConflictException} if it was opened with a different definition. Closed accounts
 * can still be read.
 */
public interface LedgerReader {

    /**
     * Returns the current balance of an account.
     *
     * @param account the account
     * @return the balance in the account's normal direction
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     */
    Money balance(Account account);

    /**
     * Returns the balance of an account just before a moment, counting transactions recorded before it.
     *
     * @param account the account
     * @param asOf    the moment; transactions recorded at or after it are not counted
     * @return the balance in the account's normal direction
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     */
    Money balanceAsOf(Account account, Instant asOf);

    /**
     * Returns every entry posted to an account, in the order they were recorded.
     *
     * @param account the account
     * @return an unmodifiable list of entries
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     */
    List<Entry> entries(Account account);

    /**
     * Returns the statement of an account for the period from {@code from} (inclusive) to {@code to}
     * (exclusive), by recording time.
     *
     * <p>Consecutive periods such as {@code [day1, day2)} and {@code [day2, day3)} never share or miss a
     * transaction.
     *
     * @param account the account
     * @param from    the start of the period, inclusive
     * @param to      the end of the period, exclusive
     * @return the statement with opening balance, lines and closing balance
     * @throws IllegalArgumentException           if {@code from} is after {@code to}
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     */
    Statement statement(Account account, Instant from, Instant to);

    /**
     * Returns the total balance of a holdable account: available plus held.
     *
     * @param account the holdable account
     * @return the sum of both balances
     * @throws AccountNotOpenException            if either account has not been opened
     * @throws AccountDefinitionConflictException if either account was opened with a different definition
     */
    Money totalBalance(HoldableAccount account);

    /**
     * Returns the holds on an account that have not been captured or released yet.
     *
     * @param account the holdable account
     * @return an unmodifiable list of active holds, in the order they were placed
     * @throws AccountNotOpenException            if the available account has not been opened
     * @throws AccountDefinitionConflictException if the available account was opened with a different
     *                                            definition
     */
    List<Hold> activeHolds(HoldableAccount account);

    /**
     * Returns the sum of the balances of every account whose id is the branch or sits under it.
     *
     * <p>For example, {@code balanceUnder(new AccountId("seller:42"))} adds {@code "seller:42:wallet"} and
     * {@code "seller:42:held"}. See {@link AccountId#isWithin(AccountId)}.
     *
     * @param branch the branch id
     * @return the total, in the accounts' shared normal direction
     * @throws AccountNotOpenException      if no opened account is within the branch
     * @throws dev.kaantopcu.kasa.core.money.CurrencyMismatchException if the accounts use different currencies
     * @throws MixedNormalBalanceException  if the accounts have different normal balances
     */
    Money balanceUnder(AccountId branch);

    /**
     * Returns the debit and credit totals of every opened account, counting transactions recorded before
     * a moment.
     *
     * @param asOf the moment; transactions recorded at or after it are not counted
     * @return the trial balance, including closed accounts
     */
    TrialBalance trialBalance(Instant asOf);

    /**
     * Returns the status of an account.
     *
     * @param account the account
     * @return whether the account is open, frozen or closed
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     */
    AccountStatus status(Account account);

    /**
     * Finds a recorded transaction by its key, including those the ledger created for reversals and holds.
     *
     * @param key the transaction's key
     * @return the posted transaction, or empty if none was recorded with {@code key}
     */
    Optional<PostedTransaction> posted(IdempotencyKey key);

    /**
     * Finds a recorded transaction by its key; a shortcut for {@link #posted(IdempotencyKey)}.
     *
     * @param key the transaction's key
     * @return the posted transaction, or empty if none was recorded with {@code key}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    default Optional<PostedTransaction> posted(String key) {
        Objects.requireNonNull(key, "key");
        return posted(new IdempotencyKey(key));
    }

    /**
     * Recomputes the hash chain and finds the first transaction whose stored hash does not match.
     *
     * <p>Use it to detect records changed outside the ledger, for example directly in a database. It reads
     * every transaction, so it takes time proportional to the size of the ledger.
     *
     * @return the key of the first broken transaction, or empty if the chain is intact
     */
    Optional<IdempotencyKey> verifyChain();
}
