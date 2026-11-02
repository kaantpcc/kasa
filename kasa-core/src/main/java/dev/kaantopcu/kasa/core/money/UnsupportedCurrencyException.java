package dev.kaantopcu.kasa.core.money;

import java.util.Currency;

public class UnsupportedCurrencyException extends IllegalArgumentException {
    public UnsupportedCurrencyException(Currency currency) {
        super("%s is not supported: it has no defined decimal places".formatted(
                currency.getCurrencyCode()));
    }
}
