package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Currency;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.unlimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class LedgerConcurrencyContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final int ORDER_COUNT = 10_000;
    private static final int HOLD_REQUEST_COUNT = 1_000;

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT, unlimited());
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT, nonNegative());
    private static final HoldableAccount SELLER = new HoldableAccount(
            SELLER_WALLET,
            account("seller-42-wallet-held", Side.CREDIT, nonNegative()));

    private Ledger ledger;

    protected abstract Ledger createLedger();

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        ledger = createLedger();
        ledger.open(CUSTOMER_PAYMENTS);
        ledger.open(SELLER_WALLET);
        ledger.open(SELLER.held());
    }

    @Test
    void keepsEveryTransactionPostedConcurrently() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int order = 0; order < ORDER_COUNT; order++) {
                Transaction sale = sale("order-" + order);
                executor.submit(() -> ledger.post(sale));
            }
        }

        assertThat(ledger.statement(SELLER_WALLET)).hasSize(ORDER_COUNT);
    }

    @Test
    void allowsOnlyOneOfConcurrentHoldsOnSameMoney() {
        ledger.post(Transaction.builder("funding")
                .debit(CUSTOMER_PAYMENTS, lira("300.00"))
                .credit(SELLER_WALLET, lira("300.00"))
                .build());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int request = 0; request < HOLD_REQUEST_COUNT; request++) {
                Hold hold = new Hold(new IdempotencyKey("payout-" + request + "-hold"), SELLER, lira("300.00"));
                executor.submit(() -> ledger.placeHold(hold));
            }
        }

        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.available())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void servesOtherRequestsAfterFailedOperation() throws InterruptedException {
        assertThatThrownBy(() -> ledger.reverse(new IdempotencyKey("order-999")))
                .isInstanceOf(TransactionNotFoundException.class);

        Thread anotherRequest = Thread.ofVirtual().start(() -> ledger.post(sale("order-1")));
        boolean finished = anotherRequest.join(Duration.ofSeconds(5));

        assertThat(finished).isTrue();
    }

    private static Transaction sale(String orderKey) {
        return Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira("1.00"))
                .credit(SELLER_WALLET, lira("1.00"))
                .build();
    }

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}