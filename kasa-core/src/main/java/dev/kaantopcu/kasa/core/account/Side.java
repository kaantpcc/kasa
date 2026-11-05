package dev.kaantopcu.kasa.core.account;

public enum Side {
    DEBIT,
    CREDIT;

    public Side opposite() {
        return switch (this) {
            case DEBIT -> CREDIT;
            case CREDIT -> DEBIT;
        };
    }
}
