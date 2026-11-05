package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final HoldableAccount SELLER = new HoldableAccount(
            account("seller-42-wallet", Side.CREDIT),
            account("seller-42-wallet-held", Side.CREDIT));
    private static final IdempotencyKey HOLD_KEY = new IdempotencyKey("payout-1-hold");

    private final InMemoryLedger ledger = new InMemoryLedger();

    @Test
    void placeMovesAmountFromAvailableToHeldAccount() {
        ledger.post(Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("600.00"))
                .credit(SELLER.available(), lira("600.00"))
                .build());

        ledger.post(new Hold(HOLD_KEY, SELLER, lira("300.00")).place());

        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
    }

    @Test
    void placeUsesHoldKeyForTransaction() {
        Transaction placement = new Hold(HOLD_KEY, SELLER, lira("300.00")).place();

        assertThat(placement.idempotencyKey()).isEqualTo(HOLD_KEY);
    }

    @Test
    void rejectsNullKey() {
        assertThatThrownBy(() -> new Hold(null, SELLER, lira("300.00")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    @Test
    void rejectsNullAccount() {
        assertThatThrownBy(() -> new Hold(HOLD_KEY, null, lira("300.00")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Hold(HOLD_KEY, SELLER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> new Hold(HOLD_KEY, SELLER, lira("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hold amount must be positive, got 0.00 TRY");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Hold(HOLD_KEY, SELLER, lira("-50.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hold amount must be positive, got -50.00 TRY");
    }

    @Test
    void rejectsAmountInDifferentCurrency() {
        assertThatThrownBy(() -> new Hold(HOLD_KEY, SELLER, Money.of("300.00", USD)))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}