package dev.kaantopcu.kasa.core.money;

import java.math.BigDecimal;
import java.util.Currency;

public class InvalidMoneyScaleException extends IllegalArgumentException {

    public InvalidMoneyScaleException(BigDecimal amount, Currency currency, Throwable cause) {
        super("%s accepts at most %d decimal places, got %s".formatted(
                currency.getCurrencyCode(),
                currency.getDefaultFractionDigits(),
                amount.toPlainString()), cause);
    }
}
