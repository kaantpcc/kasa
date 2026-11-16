package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrialBalanceTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT);

    @Test
    void isBalancedWhenDebitsEqualCredits() {
        TrialBalance trialBalance = new TrialBalance(List.of(
                new TrialBalanceLine(CUSTOMER_PAYMENTS, lira("900.00"), lira("0.00")),
                new TrialBalanceLine(SELLER_WALLET, lira("0.00"), lira("900.00"))));

        assertThat(trialBalance.isBalanced()).isTrue();
    }

    @Test
    void isNotBalancedWhenDebitsDifferFromCredits() {
        TrialBalance trialBalance = new TrialBalance(List.of(
                new TrialBalanceLine(CUSTOMER_PAYMENTS, lira("1000.00"), lira("0.00")),
                new TrialBalanceLine(SELLER_WALLET, lira("0.00"), lira("900.00"))));

        assertThat(trialBalance.isBalanced()).isFalse();
    }

    @Test
    void emptyTrialBalanceIsBalanced() {
        assertThat(new TrialBalance(List.of()).isBalanced()).isTrue();
    }

    @Test
    void returnsZeroTotalsForCurrencyWithoutAccounts() {
        TrialBalance trialBalance = new TrialBalance(List.of(
                new TrialBalanceLine(SELLER_WALLET, lira("0.00"), lira("900.00"))));

        assertThat(trialBalance.totalDebits(USD)).isEqualTo(Money.zero(USD));
        assertThat(trialBalance.totalCredits(USD)).isEqualTo(Money.zero(USD));
    }

    @Test
    void doesNotAllowLinesToBeModified() {
        TrialBalance trialBalance = new TrialBalance(List.of());
        TrialBalanceLine line = new TrialBalanceLine(SELLER_WALLET, lira("0.00"), lira("900.00"));

        assertThatThrownBy(() -> trialBalance.lines().add(line))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }
}
