package dev.kaantopcu.kasa.core.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.math.RoundingMode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency GOLD = Currency.getInstance("XAU");

    @Test
    void createsMoneyFromAmountAndCurrency() {
        Money money = Money.of("150.75", TRY);

        assertThat(money.amount()).isEqualByComparingTo("150.75");
        assertThat(money.currency()).isEqualTo(TRY);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, TRY))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> Money.of("10.00", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency");
    }

    @Test
    void treatsAmountWithDifferentTrailingZerosAsEqual() {
        assertThat(Money.of("10", TRY)).isEqualTo(Money.of("10.00", TRY));
    }

    @Test
    void scalesAmountToCurrencyDecimalPlaces() {
        assertThat(Money.of("10", TRY).amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.of("500", JPY).amount()).isEqualTo(new BigDecimal("500"));
    }

    @Test
    void rejectsAmountWithMoreDecimalPlacesThanCurrencyAllows() {
        assertThatThrownBy(() -> Money.of("10.005", TRY))
                .isInstanceOf(InvalidMoneyScaleException.class)
                .hasMessage("TRY accepts at most 2 decimal places, got 10.005");
    }

    @Test
    void rejectsFractionalAmountForCurrencyWithoutDecimals() {
        assertThatThrownBy(() -> Money.of("100.5", JPY))
                .isInstanceOf(InvalidMoneyScaleException.class)
                .hasMessage("JPY accepts at most 0 decimal places, got 100.5");
    }

    @Test
    void dropsTrailingZerosBeyondCurrencyDecimalPlaces() {
        assertThat(Money.of("10.500", TRY).amount()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void addsMoneyOfSameCurrency() {
        Money total = Money.of("100.00", TRY).add(Money.of("50.25", TRY));

        assertThat(total).isEqualTo(Money.of("150.25", TRY));
    }

    @Test
    void subtractsMoneyOfSameCurrency() {
        Money remaining = Money.of("100.00", TRY).subtract(Money.of("30.50", TRY));

        assertThat(remaining).isEqualTo(Money.of("69.50", TRY));
    }

    @Test
    void allowsNegativeResultWhenSubtracting() {
        Money result = Money.of("50.00", TRY).subtract(Money.of("80.00", TRY));

        assertThat(result).isEqualTo(Money.of("-30.00", TRY));
    }

    @Test
    void keepsOriginalUnchangedAfterAdding() {
        Money original = Money.of("100.00", TRY);

        original.add(Money.of("50.00", TRY));

        assertThat(original).isEqualTo(Money.of("100.00", TRY));
    }

    @Test
    void rejectsAddingDifferentCurrencies() {
        Money lira = Money.of("100.00", TRY);
        Money dollar = Money.of("10.00", USD);

        assertThatThrownBy(() -> lira.add(dollar))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsSubtractingDifferentCurrencies() {
        Money lira = Money.of("100.00", TRY);
        Money dollar = Money.of("10.00", USD);

        assertThatThrownBy(() -> lira.subtract(dollar))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsAddingNull() {
        Money lira = Money.of("100.00", TRY);

        assertThatThrownBy(() -> lira.add(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("other");
    }

    @Test
    void createsZeroInCurrencyScale() {
        Money zero = Money.zero(TRY);

        assertThat(zero).isEqualTo(Money.of("0.00", TRY));
        assertThat(zero.isZero()).isTrue();
    }

    @Test
    void treatsPositiveAmountsAsPositive() {
        Money money = Money.of("0.01", TRY);

        assertThat(money.isPositive()).isTrue();
        assertThat(money.isNegative()).isFalse();
        assertThat(money.isZero()).isFalse();
    }

    @Test
    void treatsNegativeAmountsAsNegative() {
        Money money = Money.of("-0.01", TRY);

        assertThat(money.isNegative()).isTrue();
        assertThat(money.isPositive()).isFalse();
        assertThat(money.isZero()).isFalse();
    }

    @Test
    void treatsZeroAsNeitherPositiveNorNegative() {
        Money zero = Money.zero(TRY);

        assertThat(zero.isPositive()).isFalse();
        assertThat(zero.isNegative()).isFalse();
    }

    @Test
    void negatesAmount() {
        assertThat(Money.of("25.50", TRY).negate()).isEqualTo(Money.of("-25.50", TRY));
        assertThat(Money.of("-25.50", TRY).negate()).isEqualTo(Money.of("25.50", TRY));
    }

    @Test
    void returnsToOriginalAfterAddingNegation() {
        Money money = Money.of("120.00", TRY);

        Money result = money.add(money.negate());

        assertThat(result.isZero()).isTrue();
    }

    @Test
    void rejectsNullAmountText() {
        assertThatThrownBy(() -> Money.of(null, TRY))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount");
    }

    @Test
    void rejectsCurrencyWithoutDefinedDecimalPlaces() {
        assertThatThrownBy(() -> Money.of("10", GOLD))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("XAU is not supported: it has no defined decimal places");
    }

    @Test
    void rejectsZeroInCurrencyWithoutDefinedDecimalPlaces() {
        assertThatThrownBy(() -> Money.zero(GOLD))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void isGreaterThanWhenAmountIsLarger() {
        assertThat(Money.of("100.00", TRY).isGreaterThan(Money.of("99.99", TRY))).isTrue();
    }

    @Test
    void isNotGreaterThanWhenAmountsAreEqual() {
        assertThat(Money.of("100.00", TRY).isGreaterThan(Money.of("100.00", TRY))).isFalse();
    }

    @Test
    void isLessThanWhenAmountIsSmaller() {
        assertThat(Money.of("99.99", TRY).isLessThan(Money.of("100.00", TRY))).isTrue();
    }

    @Test
    void isNotLessThanWhenAmountsAreEqual() {
        assertThat(Money.of("100.00", TRY).isLessThan(Money.of("100.00", TRY))).isFalse();
    }

    @Test
    void rejectsComparingDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of("1.00", TRY).isGreaterThan(Money.of("1.00", USD)))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsComparingWithNull() {
        assertThatThrownBy(() -> Money.of("1.00", TRY).isLessThan(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("other");
    }

    @Test
    void multipliesByRate() {
        Money commission = Money.of("1000.00", TRY).multiply(new BigDecimal("0.10"), RoundingMode.HALF_EVEN);

        assertThat(commission).isEqualTo(Money.of("100.00", TRY));
    }

    @Test
    void roundsHalfUpWhenAsked() {
        Money commission = Money.of("99.99", TRY).multiply(new BigDecimal("0.10"), RoundingMode.HALF_UP);

        assertThat(commission).isEqualTo(Money.of("10.00", TRY));
    }

    @Test
    void roundsDownWhenAsked() {
        Money commission = Money.of("99.99", TRY).multiply(new BigDecimal("0.10"), RoundingMode.DOWN);

        assertThat(commission).isEqualTo(Money.of("9.99", TRY));
    }

    @Test
    void roundsHalfToEvenNeighbour() {
        Money half = Money.of("10.05", TRY).multiply(new BigDecimal("0.5"), RoundingMode.HALF_EVEN);

        assertThat(half).isEqualTo(Money.of("5.02", TRY));
    }

    @Test
    void rejectsNullRate() {
        assertThatThrownBy(() -> Money.of("1.00", TRY).multiply(null, RoundingMode.HALF_EVEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rate");
    }

    @Test
    void rejectsNullRoundingMode() {
        assertThatThrownBy(() -> Money.of("1.00", TRY).multiply(new BigDecimal("0.10"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("roundingMode");
    }

    @ParameterizedTest
    @CsvSource({
            "100.00, 3",
            "100.00, 7",
            "999.99, 2",
            "0.01, 3",
            "0.00, 4"
    })
    void allocatedSharesAddUpToOriginalAmount(String amount, int parts) {
        Money original = Money.of(amount, TRY);

        List<Money> shares = original.allocate(parts);

        Money total = Money.zero(TRY);
        for (Money share : shares) {
            total = total.add(share);
        }
        assertThat(shares).hasSize(parts);
        assertThat(total).isEqualTo(original);
    }

    @Test
    void givesLeftoverCentsToFirstShares() {
        List<Money> shares = Money.of("100.00", TRY).allocate(3);

        assertThat(shares).containsExactly(
                Money.of("33.34", TRY),
                Money.of("33.33", TRY),
                Money.of("33.33", TRY));
    }

    @Test
    void allocatesCurrencyWithoutFractionDigits() {
        List<Money> shares = Money.of("100", JPY).allocate(3);

        assertThat(shares).containsExactly(
                Money.of("34", JPY),
                Money.of("33", JPY),
                Money.of("33", JPY));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveParts(int parts) {
        assertThatThrownBy(() -> Money.of("100.00", TRY).allocate(parts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parts must be positive, got " + parts);
    }

    @Test
    void rejectsAllocatingNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-100.00", TRY).allocate(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot allocate a negative amount, got -100.00 TRY");
    }
}
