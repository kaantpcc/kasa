package dev.kaantopcu.kasa.core.money;

import java.util.Currency;

public final class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Cannot combine %s with %s".formatted(
                expected.getCurrencyCode(),
                actual.getCurrencyCode()));
    }
}
