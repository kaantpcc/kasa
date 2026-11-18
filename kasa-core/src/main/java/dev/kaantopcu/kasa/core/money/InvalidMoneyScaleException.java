package dev.kaantopcu.kasa.core.money;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Thrown when an amount has more decimal places than its currency allows, for example {@code 10.001} TRY.
 *
 * <p>Kasa rejects such amounts instead of rounding them.
 */
public final class InvalidMoneyScaleException extends IllegalArgumentException {

    InvalidMoneyScaleException(BigDecimal amount, Currency currency, Throwable cause) {
        super("%s accepts at most %d decimal places, got %s".formatted(
                currency.getCurrencyCode(),
                currency.getDefaultFractionDigits(),
                amount.toPlainString()), cause);
    }
}
