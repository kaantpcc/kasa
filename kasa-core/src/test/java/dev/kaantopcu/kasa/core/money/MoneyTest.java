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
}
