/**
 * A double-entry ledger: transactions, holds, refunds, reversals, statements and an audit hash chain.
 *
 * <p>Start with {@link dev.kaantopcu.kasa.core.ledger.InMemoryLedger}, open accounts, then post balanced
 * {@link dev.kaantopcu.kasa.core.ledger.Transaction}s. The ledger is append-only: nothing is updated or
 * deleted, and a mistake is corrected with {@link dev.kaantopcu.kasa.core.ledger.LedgerWriter#reverse(IdempotencyKey)}
 * or a refund. Every transaction carries an {@link dev.kaantopcu.kasa.core.ledger.IdempotencyKey}, so a
 * retried request is recorded only once.
 *
 * <p>Code that only reads balances should depend on {@link dev.kaantopcu.kasa.core.ledger.LedgerReader},
 * code that records money on {@link dev.kaantopcu.kasa.core.ledger.LedgerWriter}, and code that manages
 * accounts on {@link dev.kaantopcu.kasa.core.ledger.AccountAdministration}.
 * {@link dev.kaantopcu.kasa.core.ledger.Ledger} combines all three.
 *
 * <p>Business rule violations are reported with subclasses of
 * {@link dev.kaantopcu.kasa.core.ledger.LedgerException}; when one is thrown, nothing has been recorded.
 * Unless a method says otherwise, passing {@code null} to any method or constructor in this package
 * throws {@link NullPointerException}.
 */
package dev.kaantopcu.kasa.core.ledger;
