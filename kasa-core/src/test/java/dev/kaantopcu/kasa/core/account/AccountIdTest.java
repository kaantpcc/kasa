package dev.kaantopcu.kasa.core.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {":seller", "seller:", "seller::wallet", "seller: :wallet"})
    void rejectsIdWithBlankSegment(String value) {
        assertThatThrownBy(() -> new AccountId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account id must not contain blank segments, got %s".formatted(value));
    }

    @ParameterizedTest
    @CsvSource({
            "seller:42:wallet, seller:42",
            "seller:42:wallet, seller",
            "seller:42:wallet, seller:42:wallet",
            "seller-42-wallet, seller-42-wallet"
    })
    void isWithinBranchThatMatchesItsLeadingSegments(String id, String branch) {
        assertThat(new AccountId(id).isWithin(new AccountId(branch))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "seller:42, seller:4",
            "seller:42, seller:42:wallet",
            "seller:42:wallet, seller:43",
            "seller:42:wallet, buyer:42",
            "seller-42-wallet, seller"
    })
    void isNotWithinBranchThatDiffersInAnySegment(String id, String branch) {
        assertThat(new AccountId(id).isWithin(new AccountId(branch))).isFalse();
    }

    @Test
    void rejectsNullBranch() {
        AccountId id = new AccountId("seller:42:wallet");

        assertThatThrownBy(() -> id.isWithin(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("branch");
    }
}
