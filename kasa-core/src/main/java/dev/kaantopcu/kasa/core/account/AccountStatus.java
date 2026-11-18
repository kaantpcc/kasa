package dev.kaantopcu.kasa.core.account;

/**
 * The lifecycle state of an opened account.
 */
public enum AccountStatus {
    /**
     * The account accepts incoming and outgoing money.
     */
    OPEN,
    /**
     * The account accepts incoming money but not outgoing money, for example during a dispute.
     */
    FROZEN,
    /**
     * The account accepts no money at all. An account can only be closed with a zero balance,
     * and closing cannot be undone.
     */
    CLOSED;

    /**
     * Tells whether money may move into or out of an account in this state.
     *
     * @return {@code true} for {@link #OPEN} and {@link #FROZEN}
     */
    public boolean acceptsMovements() {
        return switch (this) {
            case OPEN, FROZEN -> true;
            case CLOSED -> false;
        };
    }

    /**
     * Tells whether an account in this state may decrease its balance.
     *
     * @return {@code true} only for {@link #OPEN}
     */
    public boolean allowsOutgoingMoney() {
        return switch (this) {
            case OPEN -> true;
            case FROZEN, CLOSED -> false;
        };
    }
}
