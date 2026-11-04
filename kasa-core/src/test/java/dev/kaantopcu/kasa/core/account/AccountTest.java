package dev.kaantopcu.kasa.core.account;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final AccountId SELLER_WALLET = new AccountId("seller-42-wallet");
    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    void createsAccountWithIdCurrencyAndNormalBalance() {
        Account account = new Account(SELLER_WALLET, TRY, Side.CREDIT);

        assertThat(account.id()).isEqualTo(SELLER_WALLET);
        assertThat(account.currency()).isEqualTo(TRY);
        assertThat(account.normalBalance()).isEqualTo(Side.CREDIT);
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new Account(null, TRY, Side.CREDIT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Account(SELLER_WALLET, null, Side.CREDIT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency");
    }

    @Test
    void rejectsNullNormalBalance() {
        assertThatThrownBy(() -> new Account(SELLER_WALLET, TRY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("normalBalance");
    }
}
