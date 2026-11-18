/**
 * Account definitions: identity, currency, normal balance, balance policy and status.
 *
 * <p>An {@link dev.kaantopcu.kasa.core.account.Account} is a value: two accounts with the same id must have
 * the same definition. Accounts hold no money themselves; balances come from the ledger's posted entries.
 *
 * <p>Unless a method says otherwise, passing {@code null} to any method or constructor in this package
 * throws {@link NullPointerException}.
 */
package dev.kaantopcu.kasa.core.account;
