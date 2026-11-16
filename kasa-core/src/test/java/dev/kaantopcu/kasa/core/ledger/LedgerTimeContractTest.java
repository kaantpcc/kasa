package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.unlimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class LedgerTimeContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Instant START = Instant.parse("2026-11-16T09:00:00Z");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT, unlimited());
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT, nonNegative());
    private static final IdempotencyKey ORDER_KEY = new IdempotencyKey("order-1");

    private AdjustableClock clock;
    private Ledger ledger;

    protected abstract Ledger createLedger(Clock clock);

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        clock = new AdjustableClock(START, ZoneOffset.UTC);
        ledger = openedLedger(clock);
    }

    @Test
    void stampsTransactionWithClockTime() {
        ledger.post(sale().build());

        assertThat(postedOrder(ledger).recordedAt()).isEqualTo(START);
    }

    @Test
    void keepsPostedTransactionContent() {
        Transaction sale = sale().description("Order 1 payment").build();

        ledger.post(sale);

        assertThat(postedOrder(ledger).transaction()).isEqualTo(sale);
    }

    @Test
    void usesRecordingDayAsDefaultValueDate() {
        ledger.post(sale().build());

        assertThat(postedOrder(ledger).valueDate()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void readsRecordingDayInClockTimeZone() {
        Clock lateEveningUtc = new AdjustableClock(Instant.parse("2026-11-16T22:30:00Z"), ZoneId.of("Europe/Istanbul"));
        Ledger istanbulLedger = openedLedger(lateEveningUtc);

        istanbulLedger.post(sale().build());

        assertThat(postedOrder(istanbulLedger).valueDate()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void keepsValueDateGivenInTransaction() {
        ledger.post(sale().valueDate(LocalDate.of(2026, 11, 20)).build());

        assertThat(postedOrder(ledger).valueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void returnsEmptyForUnknownKey() {
        assertThat(ledger.posted(new IdempotencyKey("order-999"))).isEmpty();
    }

    @Test
    void doesNotKeepRejectedTransaction() {
        Transaction payoutWithoutFunds = Transaction.builder("payout-1")
                .debit(SELLER_WALLET, lira("100.00"))
                .credit(CUSTOMER_PAYMENTS, lira("100.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(payoutWithoutFunds))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(ledger.posted(new IdempotencyKey("payout-1"))).isEmpty();
    }

    @Test
    void stampsReversalWithItsOwnTime() {
        ledger.post(sale().build());
        clock.advance(Duration.ofHours(1));

        ledger.reverse(ORDER_KEY);

        PostedTransaction reversal = ledger.posted(new IdempotencyKey("order-1:reversal")).orElseThrow();
        assertThat(reversal.recordedAt()).isEqualTo(START.plus(Duration.ofHours(1)));
    }

    @Test
    void keepsFirstRecordedTimeWhenTransactionIsRetried() {
        ledger.post(sale().build());
        clock.advance(Duration.ofHours(1));

        ledger.post(sale().build());

        assertThat(postedOrder(ledger).recordedAt()).isEqualTo(START);
    }

    @Test
    void neverRecordsEarlierThanPreviousTransaction() {
        ledger.post(sale().build());
        clock.advance(Duration.ofHours(-1));

        ledger.post(Transaction.builder("order-2")
                .debit(CUSTOMER_PAYMENTS, lira("100.00"))
                .credit(SELLER_WALLET, lira("100.00"))
                .build());

        assertThat(ledger.posted(new IdempotencyKey("order-2")).orElseThrow().recordedAt()).isEqualTo(START);
    }

    @Test
    void rejectsNullKeyForPosted() {
        assertThatThrownBy(() -> ledger.posted(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    private Ledger openedLedger(Clock ledgerClock) {
        Ledger openedLedger = createLedger(ledgerClock);
        openedLedger.open(CUSTOMER_PAYMENTS);
        openedLedger.open(SELLER_WALLET);
        return openedLedger;
    }

    private static PostedTransaction postedOrder(Ledger ledger) {
        return ledger.posted(ORDER_KEY).orElseThrow();
    }

    private static Transaction.Builder sale() {
        return Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                .credit(SELLER_WALLET, lira("900.00"));
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }
}
