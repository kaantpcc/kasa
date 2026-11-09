package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionBuilderTest {

    private static final Currency TRY = Currency.getInstance("TRY");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT);
    private static final Account COMMISSION = account("commission", Side.CREDIT);

    @Test
    void buildsTransactionWithEntriesInOrder() {
        Transaction transaction = Transaction.builder("order-123")
                .debit(CUSTOMER_PAYMENTS, lira("1000.00"))
                .credit(SELLER_WALLET, lira("900.00"))
                .credit(COMMISSION, lira("100.00"))
                .build();

        assertThat(transaction.idempotencyKey()).isEqualTo(new IdempotencyKey("order-123"));
        assertThat(transaction.entries()).containsExactly(
                new Entry(CUSTOMER_PAYMENTS, lira("1000.00"), Side.DEBIT),
                new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT),
                new Entry(COMMISSION, lira("100.00"), Side.CREDIT));
    }

    @Test
    void rejectsUnbalancedTransactionOnBuild() {
        Transaction.Builder builder = Transaction.builder("order-123")
                .debit(CUSTOMER_PAYMENTS, lira("1000.00"))
                .credit(SELLER_WALLET, lira("900.00"));

        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessage("Transaction is unbalanced in TRY: debits minus credits = 100.00");
    }

    @Test
    void rejectsBuildWithoutEntries() {
        Transaction.Builder builder = Transaction.builder("order-123");

        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must have at least one entry");
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThatThrownBy(() -> Transaction.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key must not be blank");
    }

    @Test
    void keepsBuiltTransactionUnchangedWhenBuilderAddsMoreEntries() {
        Transaction.Builder builder = Transaction.builder("order-123")
                .debit(CUSTOMER_PAYMENTS, lira("100.00"))
                .credit(SELLER_WALLET, lira("100.00"));
        Transaction transaction = builder.build();

        builder.credit(COMMISSION, lira("50.00"));

        assertThat(transaction.entries()).hasSize(2);
    }


    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

}
