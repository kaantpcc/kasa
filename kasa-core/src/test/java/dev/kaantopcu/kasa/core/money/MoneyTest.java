package dev.kaantopcu.kasa.core.money;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.function.Try;

import java.math.BigDecimal;
import java.util.Currency;

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
}
