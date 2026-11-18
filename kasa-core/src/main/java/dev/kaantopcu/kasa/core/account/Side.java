package dev.kaantopcu.kasa.core.account;

/**
 * The side of a double-entry posting.
 *
 * <p>Every transaction has equal debit and credit totals in each currency. Whether a side increases or
 * decreases an account depends on the account's {@link Account#normalBalance() normal balance}.
 */
public enum Side {
    /**
     * The left side; increases debit-normal accounts and decreases credit-normal accounts.
     */
    DEBIT,
    /**
     * The right side; increases credit-normal accounts and decreases debit-normal accounts.
     */
    CREDIT;

    /**
     * Returns the other side.
     *
     * @return {@link #CREDIT} for {@link #DEBIT}, and {@link #DEBIT} for {@link #CREDIT}
     */
    public Side opposite() {
        return switch (this) {
            case DEBIT -> CREDIT;
            case CREDIT -> DEBIT;
        };
    }
}
