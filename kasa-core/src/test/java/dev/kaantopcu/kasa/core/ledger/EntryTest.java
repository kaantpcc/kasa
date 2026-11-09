package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Account SELLER_WALLET =
            new Account(new AccountId("seller-wallet-42"), TRY, Side.CREDIT, BalancePolicy.nonNegative());

    @Test
    void createsEntryWithAccountAmountAndSide() {
        Entry entry = new Entry(SELLER_WALLET, Money.of("900.00", TRY), Side.CREDIT);

        assertThat(entry.account()).isEqualTo(SELLER_WALLET);
        assertThat(entry.amount()).isEqualTo(Money.of("900.00", TRY));
        assertThat(entry.side()).isEqualTo(Side.CREDIT);
    }

    @Test
    void rejectsNullAccount() {
        assertThatThrownBy(() -> new Entry(null, Money.of("900.00", TRY), Side.CREDIT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Entry(SELLER_WALLET, null, Side.CREDIT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount");
    }

    @Test
    void rejectsNullSide() {
        assertThatThrownBy(() -> new Entry(SELLER_WALLET, Money.of("900.00", TRY), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("side");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Entry(SELLER_WALLET, Money.zero(TRY), Side.CREDIT))
                .isInstanceOf(InvalidEntryAmountException.class)
                .hasMessage("Entry amount must be positive, got 0.00 TRY");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Entry(SELLER_WALLET, Money.of("-300.00", TRY), Side.DEBIT))
                .isInstanceOf(InvalidEntryAmountException.class)
                .hasMessage("Entry amount must be positive, got -300.00 TRY");
    }

    @Test
    void rejectsAmountInDifferentCurrencyThanAccount() {
        assertThatThrownBy(() -> new Entry(SELLER_WALLET, Money.of("900.00", USD), Side.CREDIT))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void allowsEntryOnSideOppositeToAccountNormalBalance() {
        Entry withdrawal = new Entry(SELLER_WALLET, Money.of("300.00", TRY), Side.DEBIT);

        assertThat(withdrawal.side()).isEqualTo(Side.DEBIT);
    }

    @Test
    void reversedEntryHasOppositeSideAndSameAmount() {
        Entry entry = new Entry(SELLER_WALLET, Money.of("900.00", TRY), Side.CREDIT);

        Entry reversed = entry.reversed();

        assertThat(reversed).isEqualTo(new Entry(SELLER_WALLET, Money.of("900.00", TRY), Side.DEBIT));
    }
}
