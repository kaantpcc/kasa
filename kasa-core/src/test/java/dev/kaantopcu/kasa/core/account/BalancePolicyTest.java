package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalancePolicyTest {

    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    void createsOverdraftWithPositiveLimit() {
        BalancePolicy.Overdraft overdraft = new BalancePolicy.Overdraft(Money.of("5000.00", TRY));

        assertThat(overdraft.limit()).isEqualTo(Money.of("5000.00", TRY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-100.00"})
    void rejectsOverdraftLimitThatIsNotPositive(String limit) {
        assertThatThrownBy(() -> BalancePolicy.overdraft(Money.of(limit, TRY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Overdraft limit must be positive, got %s TRY".formatted(limit));
    }

    @Test
    void rejectsNullOverdraftLimit() {
        assertThatThrownBy(() -> BalancePolicy.overdraft(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("limit");
    }
}
