package dev.kaantopcu.kasa.core.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountIdTest {

    @Test
    void createsAccountIdFromValue() {
        AccountId id = new AccountId("seller-42-wallet");

        assertThat(id.value()).isEqualTo("seller-42-wallet");
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new AccountId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }

    @Test
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> new AccountId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account id must not be blank");
    }

    @Test
    void rejectsWhiteSpaceOnlyValue() {
        assertThatThrownBy(() -> new AccountId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account id must not be blank");
    }

    @Test
    void treatsIdsWithSameValueAsEqual() {
        assertThat(new AccountId("seller-42-wallet"))
                .isEqualTo(new AccountId("seller-42-wallet"));
    }
}
