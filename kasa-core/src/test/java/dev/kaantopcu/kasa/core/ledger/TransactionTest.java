package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");

    private static final IdempotencyKey ORDER_KEY = new IdempotencyKey("order-123");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", TRY, Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", TRY, Side.CREDIT);
    private static final Account COMMISSION = account("commission", TRY, Side.CREDIT);
    private static final Account CUSTOMER_USD = account("customer:7:usd", USD, Side.CREDIT);
    private static final Account CUSTOMER_TRY = account("customer:7:try", TRY, Side.CREDIT);
    private static final Account FX_USD = account("bank:fx:usd", USD, Side.DEBIT);
    private static final Account FX_TRY = account("bank:fx:try", TRY, Side.DEBIT);

    @Test
    void createsBalancedTransaction() {
        List<Entry> entries = List.of(
                debit(CUSTOMER_PAYMENTS, "1000.00"),
                credit(SELLER_WALLET, "900.00"),
                credit(COMMISSION, "100.00"));

        Transaction transaction = new Transaction(ORDER_KEY, entries);

        assertThat(transaction.idempotencyKey()).isEqualTo(ORDER_KEY);
        assertThat(transaction.entries()).containsExactlyElementsOf(entries);
    }

    @Test
    void rejectsNullIdempotencyKey() {
        List<Entry> entries = List.of(
                debit(CUSTOMER_PAYMENTS, "100.00"),
                credit(SELLER_WALLET, "100.00"));

        assertThatThrownBy(() -> new Transaction(null, entries))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("idempotencyKey");
    }

    @Test
    void rejectsNullEntries() {
        assertThatThrownBy(() -> new Transaction(ORDER_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("entries");
    }

    @Test
    void rejectsNullEntryInList() {
        List<Entry> entries = new ArrayList<>();
        entries.add(debit(CUSTOMER_PAYMENTS, "100.00"));
        entries.add(null);

        assertThatThrownBy(() -> new Transaction(ORDER_KEY, entries))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsTransactionWithoutEntries() {
        assertThatThrownBy(() -> new Transaction(ORDER_KEY, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction must have at least one entry");
    }

    @Test
    void rejectsTransactionWhenDebitsAndCreditsDiffer() {
        List<Entry> entries = List.of(
                debit(CUSTOMER_PAYMENTS, "1000.00"),
                credit(SELLER_WALLET, "900.00"),
                credit(COMMISSION, "50.00"));

        assertThatThrownBy(() -> new Transaction(ORDER_KEY, entries))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessage("Transaction is unbalanced in TRY: debits minus credits = 50.00");
    }

    @Test
    void rejectsSingleEntryTransaction() {
        List<Entry> entries = List.of(debit(CUSTOMER_PAYMENTS, "100.00"));

        assertThatThrownBy(() -> new Transaction(ORDER_KEY, entries))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessage("Transaction is unbalanced in TRY: debits minus credits = 100.00");
    }

    @Test
    void acceptsTransactionBalancedInEachCurrency() {
        List<Entry> entries = List.of(
                debit(CUSTOMER_USD, "100.00"),
                credit(FX_USD, "100.00"),
                debit(FX_TRY, "3400.00"),
                credit(CUSTOMER_TRY, "3400.00"));

        Transaction transaction = new Transaction(ORDER_KEY, entries);

        assertThat(transaction.entries()).hasSize(4);
    }

    @Test
    void rejectsTransactionUnbalancedInOneCurrency() {
        List<Entry> entries = List.of(
                debit(CUSTOMER_USD, "100.00"),
                credit(FX_USD, "100.00"),
                debit(FX_TRY, "3400.00"),
                credit(CUSTOMER_TRY, "3300.00"));

        assertThatThrownBy(() -> new Transaction(ORDER_KEY, entries))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessage("Transaction is unbalanced in TRY: debits minus credits = 100.00");
    }

    @Test
    void keepsEntriesUnchangedWhenOriginalListIsModified() {
        List<Entry> entries = new ArrayList<>(List.of(
                debit(CUSTOMER_PAYMENTS, "100.00"),
                credit(SELLER_WALLET, "100.00")));
        Transaction transaction = new Transaction(ORDER_KEY, entries);

        entries.add(credit(COMMISSION, "50.00"));

        assertThat(transaction.entries()).hasSize(2);
    }

    @Test
    void doesNotAllowEntriesToBeModified() {
        List<Entry> entries = new ArrayList<>(List.of(
                debit(CUSTOMER_PAYMENTS, "100.00"),
                credit(SELLER_WALLET, "100.00")));
        Transaction transaction = new Transaction(ORDER_KEY, entries);

        assertThatThrownBy(() -> transaction.entries().add(credit(COMMISSION, "50.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reversedTransactionFlipsEveryEntry() {
        Transaction original = new Transaction(ORDER_KEY, List.of(
                debit(CUSTOMER_PAYMENTS, "1000.00"),
                credit(SELLER_WALLET, "900.00"),
                credit(COMMISSION, "100.00")));

        Transaction reversal = original.reversed();

        assertThat(reversal.entries()).containsExactly(
                credit(CUSTOMER_PAYMENTS, "1000.00"),
                debit(SELLER_WALLET, "900.00"),
                debit(COMMISSION, "100.00"));
    }

    @Test
    void reversedTransactionUsesSuffixedKey() {
        Transaction original = new Transaction(ORDER_KEY, List.of(
                debit(CUSTOMER_PAYMENTS, "100.00"),
                credit(SELLER_WALLET, "100.00")));

        assertThat(original.reversed().idempotencyKey())
                .isEqualTo(new IdempotencyKey("order-123:reversal"));
    }

    private static Account account(String id, Currency currency, Side normalBalance) {
        return new Account(new AccountId(id), currency, normalBalance, BalancePolicy.unlimited());
    }

    private static Entry debit(Account account, String amount) {
        return new Entry(account, Money.of(amount, account.currency()), Side.DEBIT);
    }

    private static Entry credit(Account account, String amount) {
        return new Entry(account, Money.of(amount, account.currency()), Side.CREDIT);
    }
}