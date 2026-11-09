package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.unlimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT, unlimited());
    private static final Account BANK = account("bank", Side.DEBIT, unlimited());
    private static final HoldableAccount SELLER = new HoldableAccount(
            account("seller:42:wallet", Side.CREDIT, nonNegative()),
            account("seller:42:wallet:held", Side.CREDIT, nonNegative()));
    private static final IdempotencyKey HOLD_KEY = new IdempotencyKey("payout-1-hold");

    private final InMemoryLedger ledger = new InMemoryLedger();

    @BeforeEach
    void openAccounts() {
        ledger.open(CUSTOMER_PAYMENTS);
        ledger.open(BANK);
        ledger.open(SELLER.available());
        ledger.open(SELLER.held());
    }

    @Test
    void placeMovesAmountFromAvailableToHeldAccount() {
        fundSeller("600.00");

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
    void captureMovesHeldAmountOutToDestination() {
        fundSeller("600.00");
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        ledger.post(hold.place());

        ledger.post(hold.capture(BANK));

        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void releaseReturnsHeldAmountToAvailableAccount() {
        fundSeller("600.00");
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        ledger.post(hold.place());

        ledger.post(hold.release());

        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("600.00"));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void captureUsesSuffixedHoldKey() {
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));

        assertThat(hold.capture(BANK).idempotencyKey())
                .isEqualTo(new IdempotencyKey("payout-1-hold:capture"));
    }

    @Test
    void releaseUsesSuffixedHoldKey() {
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));

        assertThat(hold.release().idempotencyKey())
                .isEqualTo(new IdempotencyKey("payout-1-hold:release"));
    }

    @Test
    void rejectsNullCaptureDestination() {
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));

        assertThatThrownBy(() -> hold.capture(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("destination");
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

    private void fundSeller(String amount) {
        ledger.post(Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(SELLER.available(), lira(amount))
                .build());
    }

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}