package dev.kaantopcu.kasa.core.ledger;

public abstract sealed class LedgerException extends RuntimeException
        permits AccountNotOpenException,
                AccountDefinitionConflictException,
                AccountFrozenException,
                AccountClosedException,
                NonZeroBalanceException,
                InsufficientFundsException,
                IdempotencyKeyConflictException,
                TransactionNotFoundException {

    protected LedgerException(String message) {
        super(message);
    }
}
