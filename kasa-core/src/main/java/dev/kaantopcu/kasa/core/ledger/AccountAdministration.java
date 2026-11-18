package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;

/**
 * Opens accounts and changes their status.
 *
 * <p>Every operation can be repeated safely: opening an open account, freezing a frozen account or closing
 * a closed account has no effect. The account passed in must have the same definition as when it was
 * opened, otherwise {@link AccountDefinitionConflictException} is thrown.
 */
public interface AccountAdministration {

    /**
     * Opens an account so that money can be posted to it.
     *
     * @param account the account to open
     * @throws AccountDefinitionConflictException if an account with the same id is open with a different
     *                                            definition
     * @throws AccountClosedException             if the account was closed
     */
    void open(Account account);

    /**
     * Opens both accounts of a holdable account and marks the held account so that it changes only through
     * the hold operations.
     *
     * <p>Either both accounts are opened or neither is. The held account must start empty: its balance must
     * always equal the sum of its active holds.
     *
     * @param account the holdable account to open
     * @throws AccountDefinitionConflictException if either id is open with a different definition
     * @throws AccountClosedException             if either account was closed
     * @throws HeldAccountNotEmptyException       if the held account already received money in another way
     */
    void openHoldable(HoldableAccount account);

    /**
     * Freezes an account: it can still receive money but cannot send any.
     *
     * @param account the account to freeze
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     * @throws AccountClosedException             if the account is closed
     */
    void freeze(Account account);

    /**
     * Unfreezes a frozen account so that it can send money again.
     *
     * @param account the account to unfreeze
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     * @throws AccountClosedException             if the account is closed
     */
    void unfreeze(Account account);

    /**
     * Closes an account for good. Its history stays in the ledger and can still be read.
     *
     * @param account the account to close
     * @throws AccountNotOpenException            if the account has not been opened
     * @throws AccountDefinitionConflictException if the account was opened with a different definition
     * @throws NonZeroBalanceException            if the account's balance is not zero
     * @throws ActiveHoldsException               if the account is the available account of a hold that is
     *                                            still active
     */
    void close(Account account);
}
