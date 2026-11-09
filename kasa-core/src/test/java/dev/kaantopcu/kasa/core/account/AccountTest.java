package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final AccountId SELLER_WALLET = new AccountId("seller:42:wallet");
    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void createsAccountWithIdCurrencyNormalBalanceAndBalancePolicy() {
        Account account = new Account(SELLER_WALLET, TRY, Side.CREDIT, BalancePolicy.nonNegative());

        assertThat(account.id()).isEqualTo(SELLER_WALLET);
        assertThat(account.currency()).isEqualTo(TRY);
        assertThat(account.normalBalance()).isEqualTo(Side.CREDIT);
        assertThat(account.balancePolicy()).isEqualTo(BalancePolicy.nonNegative());
    }

    @Test
    void acceptsOverdraftLimitInAccountCurrency() {
        BalancePolicy overdraft = BalancePolicy.overdraft(Money.of("5000.00", TRY));

        Account account = new Account(SELLER_WALLET, TRY, Side.CREDIT, overdraft);

        assertThat(account.balancePolicy()).isEqualTo(overdraft);
    }

    @Test
    void rejectsOverdraftLimitInDifferentCurrency() {
        BalancePolicy overdraftInUsd = BalancePolicy.overdraft(Money.of("5000.00", USD));

        assertThatThrownBy(() -> new Account(SELLER_WALLET, TRY, Side.CREDIT, overdraftInUsd))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new Account(null, TRY, Side.CREDIT, BalancePolicy.nonNegative()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Account(SELLER_WALLET, null, Side.CREDIT, BalancePolicy.nonNegative()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency");
    }

    @Test
    void rejectsNullNormalBalance() {
        assertThatThrownBy(() -> new Account(SELLER_WALLET, TRY, null, BalancePolicy.nonNegative()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("normalBalance");
    }

    @Test
    void rejectsNullBalancePolicy() {
        assertThatThrownBy(() -> new Account(SELLER_WALLET, TRY, Side.CREDIT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("balancePolicy");
    }
}
