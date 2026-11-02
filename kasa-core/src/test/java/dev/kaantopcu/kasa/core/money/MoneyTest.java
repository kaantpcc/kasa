package dev.kaantopcu.kasa.core.money;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency TRY = Currency.getInstance("TRY");

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
}
