package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT);
    private static final Account COMMISSION = account("commission", Side.CREDIT);
    private static final Account BANK = account("bank", Side.DEBIT);

    private final InMemoryLedger ledger = new InMemoryLedger();

    @Test
    void returnsZeroBalanceWhenLedgerIsEmpty() {
        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void readsEachAccountBalanceOnItsNormalSide() {
        ledger.post(sale("order-1"));

        assertThat(ledger.balance(CUSTOMER_PAYMENTS)).isEqualTo(lira("1000.00"));
        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
        assertThat(ledger.balance(COMMISSION)).isEqualTo(lira("100.00"));
    }

    @Test
    void subtractsEntriesOnOppositeSideOfNormalBalance() {
        ledger.post(sale("order-1"));

        ledger.post(Transaction.builder("payout-1")
                .debit(SELLER_WALLET, lira("300.00"))
                .credit(BANK, lira("300.00"))
                .build());

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    @Test
    void addsUpEntriesFromSeveralTransactions() {
        ledger.post(sale("order-1"));
        ledger.post(sale("order-2"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("1800.00"));
    }

    @Test
    void ignoresTransactionPostedTwiceWithSameKey() {
        ledger.post(sale("order-1"));

        ledger.post(sale("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void keepsSingleCopyOfEntriesWhenTransactionIsPostedTwice() {
        ledger.post(sale("order-1"));

        ledger.post(sale("order-1"));

        assertThat(ledger.statement(SELLER_WALLET)).hasSize(1);
    }

    @Test
    void rejectsSameKeyUsedForDifferentTransaction() {
        ledger.post(sale("order-1"));
        Transaction differentOrder = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("500.00"))
                .credit(SELLER_WALLET, lira("500.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(differentOrder))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Idempotency key order-1 was already used for a different transaction");
    }

    @Test
    void keepsOriginalTransactionWhenKeyConflicts() {
        ledger.post(sale("order-1"));
        Transaction differentOrder = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("500.00"))
                .credit(SELLER_WALLET, lira("500.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(differentOrder))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void ignoresEntriesOfAnotherAccounts() {
        ledger.post(sale("order-1"));

        assertThat(ledger.balance(BANK)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsNullTransaction() {
        assertThatThrownBy(() -> ledger.post(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("transaction");
    }

    @Test
    void rejectsNullAccount() {
        assertThatThrownBy(() -> ledger.balance(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void returnsEmptyStatementWhenAccountHasNoEntries() {
        ledger.post(sale("order-1"));

        assertThat(ledger.statement(BANK)).isEmpty();
    }

    @Test
    void returnsOnlyEntriesOfGivenAccountInPostingOrder() {
        ledger.post(sale("order-1"));
        ledger.post(Transaction.builder("payout-1")
                .debit(SELLER_WALLET, lira("300.00"))
                .credit(BANK, lira("300.00"))
                .build());

        assertThat(ledger.statement(SELLER_WALLET)).containsExactly(
                new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT),
                new Entry(SELLER_WALLET, lira("300.00"), Side.DEBIT));
    }

    @Test
    void doesNotAllowStatementToBeModified() {
        ledger.post(sale("order-1"));

        assertThatThrownBy(() -> ledger.statement(SELLER_WALLET).add(
                new Entry(SELLER_WALLET, lira("50.00"), Side.CREDIT)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullAccountForStatement() {
        assertThatThrownBy(() -> ledger.statement(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void reversalCancelsOriginalTransaction() {
        ledger.post(sale("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(COMMISSION)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void keepsOriginalAndReversalEntriesInStatement() {
        ledger.post(sale("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.statement(SELLER_WALLET)).containsExactly(
                new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT),
                new Entry(SELLER_WALLET, lira("900.00"), Side.DEBIT));
    }

    @Test
    void ignoresSecondReversalOfSameTransaction() {
        ledger.post(sale("order-1"));
        ledger.reverse(new IdempotencyKey("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsReversalOfUnknownTransaction() {
        assertThatThrownBy(() -> ledger.reverse(new IdempotencyKey("order-999")))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("No transaction posted with idempotency key order-999");
    }

    @Test
    void rejectsNullKeyForReversal() {
        assertThatThrownBy(() -> ledger.reverse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    private static Transaction sale(String orderKey) {
        return Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira("1000.00"))
                .credit(SELLER_WALLET, lira("900.00"))
                .credit(COMMISSION, lira("100.00"))
                .build();
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}
