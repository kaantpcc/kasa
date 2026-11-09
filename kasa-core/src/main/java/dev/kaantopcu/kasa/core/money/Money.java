package dev.kaantopcu.kasa.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        requireDecimalCurrency(currency);

        amount = withCurrencyScale(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        return new Money(new BigDecimal(amount), currency);
    }

    private static BigDecimal withCurrencyScale(BigDecimal amount, Currency currency) {
        try {
            return amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidMoneyScaleException(amount, currency, e);
        }
    }

    private static void requireDecimalCurrency(Currency currency) {
        if (currency.getDefaultFractionDigits() < 0) {
            throw new UnsupportedCurrencyException(currency);
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public Money multiply(BigDecimal rate, RoundingMode roundingMode) {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(roundingMode, "roundingMode");
        BigDecimal product = amount.multiply(rate)
                .setScale(currency.getDefaultFractionDigits(), roundingMode);
        return new Money(product, currency);
    }

    public List<Money> allocate(int parts) {
        requirePositiveParts(parts);
        requireNonNegativeAmount();
        int digits = currency.getDefaultFractionDigits();
        BigDecimal share = amount.divide(BigDecimal.valueOf(parts), digits, RoundingMode.DOWN);
        BigDecimal remainder = amount.subtract(share.multiply(BigDecimal.valueOf(parts)));
        int leftoverUnits = remainder.movePointRight(digits).intValueExact();
        BigDecimal smallestUnit = BigDecimal.ONE.movePointLeft(digits);
        List<Money> shares = new ArrayList<>();
        for (int index = 0; index < parts; index++) {
            BigDecimal extra = index < leftoverUnits ? smallestUnit : BigDecimal.ZERO;
            shares.add(new Money(share.add(extra), currency));
        }
        return List.copyOf(shares);
    }

    private static void requirePositiveParts(int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("Parts must be positive, got %d".formatted(parts));
        }
    }

    private void requireNonNegativeAmount() {
        if (isNegative()) {
            throw new IllegalArgumentException("Cannot allocate a negative amount, got %s %s".formatted(
                    amount.toPlainString(), currency.getCurrencyCode()));
        }
    }
}
