package dev.kaantopcu.kasa.core.ledger;

/**
 * The base of every business rule the ledger enforces when it records or reads money.
 *
 * <p>The ledger checks all rules before it writes anything, so when a {@code LedgerException} is thrown,
 * nothing has been recorded. The class is sealed: a {@code switch} over its subclasses can handle every
 * rejection without a default branch. Invalid input, such as an unbalanced transaction, is reported with
 * {@link IllegalArgumentException} subclasses instead, because it is a programming error rather than a
 * business outcome.
 */
public abstract sealed class LedgerException extends RuntimeException
        permits AccountNotOpenException,
                AccountDefinitionConflictException,
                AccountFrozenException,
                AccountClosedException,
                NonZeroBalanceException,
                InsufficientFundsException,
                IdempotencyKeyConflictException,
                TransactionNotFoundException,
                RefundExceedsOriginalException,
                HoldAlreadySettledException,
                HeldAccountPostingException,
                ActiveHoldsException,
                HeldAccountNotEmptyException {

    LedgerException(String message) {
        super(message);
    }
}
