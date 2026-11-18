package dev.kaantopcu.kasa.core.money;

import java.util.Currency;

/**
 * Thrown when a currency has no defined number of decimal places, such as gold ({@code XAU}),
 * and so cannot be used for {@link Money}.
 */
public final class UnsupportedCurrencyException extends IllegalArgumentException {
    UnsupportedCurrencyException(Currency currency) {
        super("%s is not supported: it has no defined decimal places".formatted(
                currency.getCurrencyCode()));
    }
}
