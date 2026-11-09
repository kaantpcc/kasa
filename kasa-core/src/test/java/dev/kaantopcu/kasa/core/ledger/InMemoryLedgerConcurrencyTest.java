package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLedgerConcurrencyTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final int ORDER_COUNT = 10_000;

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT);

    private final InMemoryLedger ledger = new InMemoryLedger();

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

    private static Transaction sale(String orderKey) {
        return Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira("1.00"))
                .credit(SELLER_WALLET, lira("1.00"))
                .build();
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}