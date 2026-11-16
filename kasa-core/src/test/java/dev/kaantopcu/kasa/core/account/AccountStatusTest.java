package dev.kaantopcu.kasa.core.account;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN, true",
            "FROZEN, true",
            "CLOSED, false"
    })
    void acceptsMovementsUnlessClosed(AccountStatus status, boolean acceptsMovements) {
        assertThat(status.acceptsMovements()).isEqualTo(acceptsMovements);
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN, true",
            "FROZEN, false",
            "CLOSED, false"
    })
    void allowsOutgoingMoneyOnlyWhenOpen(AccountStatus status, boolean allowsOutgoingMoney) {
        assertThat(status.allowsOutgoingMoney()).isEqualTo(allowsOutgoingMoney);
    }
}
