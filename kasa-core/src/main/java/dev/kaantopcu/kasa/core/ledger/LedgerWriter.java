package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Objects;

/**
 * Records money movements: transactions, holds and reversals.
 *
 * <p>Every operation is idempotent. Repeating it with the same key and the same content has no effect, so
 * a retried request is safe. Each operation checks every rule before writing anything; if it throws,
 * nothing has been recorded.
 */
public interface LedgerWriter {

    /**
     * Records a transaction and updates the balances of its accounts.
     *
     * <p>If a transaction with the same key was already posted, posting the same transaction again has no
     * effect. If the transaction has {@link Transaction#refundOf() refundOf}, it may give back at most what
     * the original moved, counting earlier refunds net of anything that later gave them back, such as their
     * reversals.
     *
     * @param transaction the transaction to record
     * @throws IllegalArgumentException           if the key ends with {@code ":reversal"}, {@code ":capture"}
     *                                            or {@code ":release"}, which are reserved for transactions the
     *                                            ledger creates
     * @throws IdempotencyKeyConflictException    if the key was already used for a different transaction
     * @throws AccountNotOpenException            if an account has not been opened
     * @throws AccountDefinitionConflictException if an account was opened with a different definition
     * @throws HeldAccountPostingException        if an entry posts to the held account of a holdable account
     * @throws TransactionNotFoundException       if the refunded transaction does not exist
     * @throws RefundExceedsOriginalException     if the refund gives back more than the original moved
     * @throws AccountClosedException             if an account is closed
     * @throws AccountFrozenException             if money would leave a frozen account
     * @throws InsufficientFundsException         if an account's balance policy would be broken
     */
    void post(Transaction transaction);

    /**
     * Puts money on hold: moves it from the available account to the held account.
     *
     * <p>The held money still belongs to the owner but can no longer be spent. Placing the same hold again
     * has no effect.
     *
     * @param hold the hold to place
     * @throws IllegalArgumentException           if the key ends with {@code ":reversal"}, {@code ":capture"}
     *                                            or {@code ":release"}, which are reserved for transactions the
     *                                            ledger creates
     * @throws IdempotencyKeyConflictException    if the key was already used for something else
     * @throws AccountNotOpenException            if either account has not been opened
     * @throws AccountDefinitionConflictException if either account was opened with a different definition
     * @throws AccountClosedException             if either account is closed
     * @throws AccountFrozenException             if the available account is frozen
     * @throws InsufficientFundsException         if the available account's balance policy would be broken
     * @throws HeldAccountNotEmptyException       if this is the first hold on the held account and it already
     *                                            received money in another way
     */
    void placeHold(Hold hold);

    /**
     * Settles a hold by sending all or part of it to a destination; the rest returns to the available account.
     *
     * <p>Repeating exactly the same capture has no effect. A hold is settled once, so capturing a hold that
     * was released, or capturing again with a different destination or amount, is rejected.
     *
     * @param holdKey     the key of the hold
     * @param destination the account that receives the captured amount
     * @param amount      the amount to capture; positive and at most the held amount
     * @throws TransactionNotFoundException if no hold was placed with {@code holdKey}
     * @throws IllegalArgumentException     if {@code amount} is not positive or exceeds the held amount
     * @throws dev.kaantopcu.kasa.core.money.CurrencyMismatchException if {@code amount} or
     *                                      {@code destination} is in another currency
     * @throws HoldAlreadySettledException  if the hold was already settled differently
     * @throws HeldAccountPostingException  if {@code destination} is the held account of a holdable account
     * @throws AccountNotOpenException      if {@code destination} has not been opened
     * @throws AccountClosedException       if an account that receives money is closed
     */
    void captureHold(IdempotencyKey holdKey, Account destination, Money amount);

    /**
     * Settles a hold by sending all or part of it to a destination; a shortcut for
     * {@link #captureHold(IdempotencyKey, Account, Money)}.
     *
     * @param holdKey     the key of the hold
     * @param destination the account that receives the captured amount
     * @param amount      the amount to capture; positive and at most the held amount
     * @throws IllegalArgumentException if {@code holdKey} is blank, or as described for the main method
     */
    default void captureHold(String holdKey, Account destination, Money amount) {
        Objects.requireNonNull(holdKey, "holdKey");
        captureHold(new IdempotencyKey(holdKey), destination, amount);
    }

    /**
     * Settles a hold by returning all of it to the available account.
     *
     * <p>Repeating the release has no effect. Releasing a hold that was captured is rejected.
     *
     * @param holdKey the key of the hold
     * @throws TransactionNotFoundException if no hold was placed with {@code holdKey}
     * @throws HoldAlreadySettledException  if the hold was already captured
     * @throws AccountClosedException       if the available account is closed
     */
    void releaseHold(IdempotencyKey holdKey);

    /**
     * Settles a hold by returning all of it to the available account; a shortcut for
     * {@link #releaseHold(IdempotencyKey)}.
     *
     * @param holdKey the key of the hold
     * @throws IllegalArgumentException if {@code holdKey} is blank
     */
    default void releaseHold(String holdKey) {
        Objects.requireNonNull(holdKey, "holdKey");
        releaseHold(new IdempotencyKey(holdKey));
    }

    /**
     * Cancels a posted transaction by posting its exact opposite.
     *
     * <p>The reversal has the key {@code key + ":reversal"}, the original's value date, and counts as a
     * refund of the original. The original stays in the ledger. Reversing again has no effect. A transaction
     * that was already partly refunded cannot be reversed, and neither can hold transactions or reversals; to
     * apply a reversed transaction again, post it with a new key.
     *
     * @param key the key of the transaction to reverse
     * @throws IllegalArgumentException       if {@code key} ends with {@code ":reversal"}, that is, it names a
     *                                        reversal
     * @throws TransactionNotFoundException   if no transaction was posted with {@code key}
     * @throws RefundExceedsOriginalException if the transaction was already partly or fully refunded
     * @throws HeldAccountPostingException    if the transaction touches a held account
     * @throws AccountClosedException         if an account is closed
     * @throws AccountFrozenException         if money would leave a frozen account
     * @throws InsufficientFundsException     if an account's balance policy would be broken
     */
    void reverse(IdempotencyKey key);

    /**
     * Cancels a posted transaction by posting its exact opposite; a shortcut for
     * {@link #reverse(IdempotencyKey)}.
     *
     * @param key the key of the transaction to reverse
     * @throws IllegalArgumentException if {@code key} is blank
     */
    default void reverse(String key) {
        Objects.requireNonNull(key, "key");
        reverse(new IdempotencyKey(key));
    }
}
