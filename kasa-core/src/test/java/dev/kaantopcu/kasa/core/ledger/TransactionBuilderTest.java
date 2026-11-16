package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionBuilderTest {

    private static final Currency TRY = Currency.getInstance("TRY");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT);
    private static final Account COMMISSION = account("commission", Side.CREDIT);
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 11, 16);

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

    @Test
    void leavesValueDateAndDescriptionEmptyByDefault() {
        Transaction transaction = sale().build();

        assertThat(transaction.valueDate()).isEmpty();
        assertThat(transaction.description()).isEmpty();
    }

    @Test
    void keepsValueDateGivenToBuilder() {
        Transaction transaction = sale().valueDate(VALUE_DATE).build();

        assertThat(transaction.valueDate()).contains(VALUE_DATE);
    }

    @Test
    void keepsDescriptionGivenToBuilder() {
        Transaction transaction = sale().description("Order 123 payment").build();

        assertThat(transaction.description()).contains("Order 123 payment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankDescription(String description) {
        Transaction.Builder builder = sale().description(description);

        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction description must not be blank");
    }

    @Test
    void rejectsNullValueDate() {
        Transaction.Builder builder = sale();

        assertThatThrownBy(() -> builder.valueDate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("valueDate");
    }

    @Test
    void rejectsNullDescription() {
        Transaction.Builder builder = sale();

        assertThatThrownBy(() -> builder.description(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("description");
    }

    private static Transaction.Builder sale() {
        return Transaction.builder("order-123")
                .debit(CUSTOMER_PAYMENTS, lira("100.00"))
                .credit(SELLER_WALLET, lira("100.00"));
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

}
