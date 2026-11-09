package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldableAccountTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final AccountId HELD_ID = new AccountId("seller-42-wallet-held");

    private static final Account SELLER_WALLET =
            new Account(new AccountId("seller-42-wallet"), TRY, Side.CREDIT, BalancePolicy.nonNegative());
    private static final Account SELLER_WALLET_HELD =
            new Account(HELD_ID, TRY, Side.CREDIT, BalancePolicy.nonNegative());

    @Test
    void createsHoldableAccountWithAvailableAndHeldAccounts() {
        HoldableAccount account = new HoldableAccount(SELLER_WALLET, SELLER_WALLET_HELD);

        assertThat(account.available()).isEqualTo(SELLER_WALLET);
        assertThat(account.held()).isEqualTo(SELLER_WALLET_HELD);
    }

    @Test
    void rejectsNullAvailableAccount() {
        assertThatThrownBy(() -> new HoldableAccount(null, SELLER_WALLET_HELD))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("available");
    }

    @Test
    void rejectsNullHeldAccount() {
        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("held");
    }

    @Test
    void rejectsSameAccountAsAvailableAndHeld() {
        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, SELLER_WALLET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Held account must differ from available account, got seller-42-wallet");
    }

    @Test
    void rejectsHeldAccountInDifferentCurrency() {
        Account heldInUsd = new Account(HELD_ID, USD, Side.CREDIT, BalancePolicy.nonNegative());

        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, heldInUsd))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsHeldAccountWithDifferentNormalBalance() {
        Account heldOnOtherSide = new Account(HELD_ID, TRY, Side.DEBIT, BalancePolicy.nonNegative());

        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, heldOnOtherSide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Held account must have the same normal balance as available account");
    }

    @Test
    void rejectsUnlimitedHeldAccount() {
        Account unlimitedHeld = new Account(HELD_ID, TRY, Side.CREDIT, BalancePolicy.unlimited());

        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, unlimitedHeld))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Held account seller-42-wallet-held must have a non-negative balance policy");
    }

    @Test
    void rejectsHeldAccountWithOverdraft() {
        Account heldWithOverdraft = new Account(HELD_ID, TRY, Side.CREDIT, BalancePolicy.overdraft(Money.of("500.00", TRY)));

        assertThatThrownBy(() -> new HoldableAccount(SELLER_WALLET, heldWithOverdraft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Held account seller-42-wallet-held must have a non-negative balance policy");
    }
}
