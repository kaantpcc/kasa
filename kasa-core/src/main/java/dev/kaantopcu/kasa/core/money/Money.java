package dev.kaantopcu.kasa.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An amount of money in one currency.
 *
 * <p>The amount always has exactly as many decimal places as the currency defines
 * ({@link Currency#getDefaultFractionDigits()}), so {@code 10} TRY and {@code 10.00} TRY are equal.
 * Money is never rounded silently: an amount with more decimal places than the currency allows is
 * rejected, and the only rounding operation, {@link #multiply(BigDecimal, RoundingMode)}, takes the
 * rounding mode explicitly. Amounts in different currencies cannot be combined.
 *
 * <p>Instances are immutable; every operation returns a new {@code Money}.
 *
 * @param amount   the amount; may be zero or negative
 * @param currency the currency of the amount
 */
public record Money(BigDecimal amount, Currency currency) {

    /**
     * Creates an amount in the given currency.
     *
     * @param amount   the amount, with at most as many decimal places as the currency defines
     * @param currency a currency with a defined number of decimal places
     * @throws InvalidMoneyScaleException   if {@code amount} has more decimal places than the currency allows
     * @throws UnsupportedCurrencyException if the currency has no defined number of decimal places
     */
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        requireDecimalCurrency(currency);

        amount = withCurrencyScale(amount, currency);
    }

    /**
     * Creates an amount from its decimal text, for example {@code Money.of("100.00", lira)}.
     *
     * @param amount   the amount as a decimal number, such as {@code "100.00"} or {@code "-5"}
     * @param currency the currency of the amount
     * @return the amount
     * @throws NumberFormatException        if {@code amount} is not a decimal number
     * @throws InvalidMoneyScaleException   if {@code amount} has more decimal places than the currency allows
     * @throws UnsupportedCurrencyException if the currency has no defined number of decimal places
     */
    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        return new Money(new BigDecimal(amount), currency);
    }

    /**
     * Creates an amount from its decimal text and an ISO 4217 currency code, for example
     * {@code Money.of("100.00", "TRY")}.
     *
     * @param amount       the amount as a decimal number, such as {@code "100.00"} or {@code "-5"}
     * @param currencyCode the ISO 4217 code of the currency, such as {@code "TRY"} or {@code "EUR"}
     * @return the amount
     * @throws IllegalArgumentException     if {@code currencyCode} is not a known ISO 4217 code
     * @throws NumberFormatException        if {@code amount} is not a decimal number
     * @throws InvalidMoneyScaleException   if {@code amount} has more decimal places than the currency allows
     * @throws UnsupportedCurrencyException if the currency has no defined number of decimal places
     */
    public static Money of(String amount, String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode");
        return of(amount, Currency.getInstance(currencyCode));
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

    /**
     * Returns the sum of this amount and another.
     *
     * @param other the amount to add, in the same currency
     * @return the sum
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Returns this amount minus another.
     *
     * @param other the amount to subtract, in the same currency
     * @return the difference, which may be negative
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     */
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

    /**
     * Returns zero in the given currency.
     *
     * @param currency the currency
     * @return zero, with the currency's number of decimal places
     * @throws UnsupportedCurrencyException if the currency has no defined number of decimal places
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Tells whether this amount is zero.
     *
     * @return {@code true} if the amount is zero
     */
    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * Tells whether this amount is below zero.
     *
     * @return {@code true} if the amount is negative
     */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /**
     * Tells whether this amount is above zero.
     *
     * @return {@code true} if the amount is positive
     */
    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /**
     * Returns this amount with the opposite sign.
     *
     * @return the negated amount
     */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /**
     * Tells whether this amount is greater than another.
     *
     * @param other the amount to compare with, in the same currency
     * @return {@code true} if this amount is strictly greater
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     */
    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    /**
     * Tells whether this amount is less than another.
     *
     * @param other the amount to compare with, in the same currency
     * @return {@code true} if this amount is strictly less
     * @throws CurrencyMismatchException if {@code other} is in a different currency
     */
    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    /**
     * Multiplies this amount by a rate and rounds the result to the currency's decimal places.
     *
     * <p>Use it for commissions and taxes, for example a 2.5% fee:
     * {@code price.multiply(new BigDecimal("0.025"), RoundingMode.HALF_EVEN)}.
     *
     * @param rate         the rate to multiply by
     * @param roundingMode how to round the result to the currency's decimal places
     * @return the rounded product
     * @throws ArithmeticException if {@code roundingMode} is {@link RoundingMode#UNNECESSARY} and the
     *                             product needs rounding
     */
    public Money multiply(BigDecimal rate, RoundingMode roundingMode) {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(roundingMode, "roundingMode");
        BigDecimal product = amount.multiply(rate)
                .setScale(currency.getDefaultFractionDigits(), roundingMode);
        return new Money(product, currency);
    }

    /**
     * Splits this amount into equal shares without losing any of it.
     *
     * <p>When the amount does not divide evenly, the first shares get one smallest unit of the currency
     * more than the others. For example, 100.00 TRY in 3 parts is 33.34, 33.33 and 33.33. The shares
     * always add up to this amount.
     *
     * @param parts the number of shares
     * @return an unmodifiable list of {@code parts} shares, largest first
     * @throws IllegalArgumentException if {@code parts} is not positive or this amount is negative
     */
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
