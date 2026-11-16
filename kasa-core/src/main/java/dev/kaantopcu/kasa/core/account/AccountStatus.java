package dev.kaantopcu.kasa.core.account;

public enum AccountStatus {
    OPEN,
    FROZEN,
    CLOSED;

    public boolean acceptsMovements() {
        return switch (this) {
            case OPEN, FROZEN -> true;
            case CLOSED -> false;
        };
    }

    public boolean allowsOutgoingMoney() {
        return switch (this) {
            case OPEN -> true;
            case FROZEN, CLOSED -> false;
        };
    }
}
