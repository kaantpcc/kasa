package dev.kaantopcu.kasa.core.money;

import java.util.Currency;

/**
 * Thrown when amounts, accounts or limits in different currencies are combined.
 *
 * <p>Kasa never converts between currencies on its own.
 */
public final class CurrencyMismatchException extends IllegalArgumentException {

    /**
     * Creates the exception for two currencies that cannot be combined.
     *
     * @param expected the currency that was required
     * @param actual   the currency that was given
     */
    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Cannot combine %s with %s".formatted(
                expected.getCurrencyCode(),
                actual.getCurrencyCode()));
    }
}
