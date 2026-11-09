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

abstract class AccountHierarchyContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");

    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", TRY, Side.DEBIT, unlimited());
    private static final HoldableAccount SELLER_42 = new HoldableAccount(
            account("seller:42:wallet", TRY, Side.CREDIT, nonNegative()),
            account("seller:42:wallet:held", TRY, Side.CREDIT, nonNegative()));
    private static final Account SELLER_42_REFUND_RESERVE = account("seller:42:refund-reserve", TRY, Side.CREDIT, nonNegative());
    private static final Account SELLER_43_WALLET = account("seller:43:wallet", TRY, Side.CREDIT, nonNegative());
    private static final Account SELLER_4_WALLET = account("seller:4:wallet", TRY, Side.CREDIT, nonNegative());

    private Ledger ledger;

    protected abstract Ledger createLedger();

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        ledger = createLedger();
        ledger.open(CUSTOMER_PAYMENTS);
        ledger.open(SELLER_42.available());
        ledger.open(SELLER_42.held());
        ledger.open(SELLER_42_REFUND_RESERVE);
        ledger.open(SELLER_43_WALLET);
        ledger.open(SELLER_4_WALLET);
    }

    @Test
    void sumsEveryAccountOfOneSeller() {
        pay("order-1", SELLER_42.available(), "900.00");
        pay("order-2", SELLER_42_REFUND_RESERVE, "100.00");
        ledger.placeHold(new Hold(new IdempotencyKey("payout-1-hold"), SELLER_42, lira("300.00")));

        assertThat(ledger.balanceUnder(new AccountId("seller:42"))).isEqualTo(lira("1000.00"));
    }

    @Test
    void sumsAccountsOfEverySeller() {
        pay("order-1", SELLER_42.available(), "900.00");
        pay("order-2", SELLER_42_REFUND_RESERVE, "100.00");
        pay("order-3", SELLER_43_WALLET, "500.00");
        pay("order-4", SELLER_4_WALLET, "50.00");

        assertThat(ledger.balanceUnder(new AccountId("seller"))).isEqualTo(lira("1550.00"));
    }

    @Test
    void doesNotMixSellersWhoseIdsShareLeadingCharacters() {
        pay("order-1", SELLER_42.available(), "900.00");
        pay("order-2", SELLER_4_WALLET, "50.00");

        assertThat(ledger.balanceUnder(new AccountId("seller:4"))).isEqualTo(lira("50.00"));
    }

    @Test
    void walletBranchMatchesTotalBalanceOfHoldableAccount() {
        pay("order-1", SELLER_42.available(), "900.00");
        ledger.placeHold(new Hold(new IdempotencyKey("payout-1-hold"), SELLER_42, lira("300.00")));

        assertThat(ledger.balanceUnder(new AccountId("seller:42:wallet")))
                .isEqualTo(ledger.totalBalance(SELLER_42));
    }

    @Test
    void includesFrozenAccounts() {
        pay("order-1", SELLER_43_WALLET, "500.00");

        ledger.freeze(SELLER_43_WALLET);

        assertThat(ledger.balanceUnder(new AccountId("seller:43"))).isEqualTo(lira("500.00"));
    }

    @Test
    void returnsZeroForBranchWithoutEntries() {
        assertThat(ledger.balanceUnder(new AccountId("seller:43"))).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsBranchWithoutOpenAccounts() {
        AccountId unknownSeller = new AccountId("seller:99");

        assertThatThrownBy(() -> ledger.balanceUnder(unknownSeller))
                .isInstanceOf(AccountNotOpenException.class)
                .hasMessage("Account seller:99 has not been opened");
    }

    @Test
    void rejectsBranchMixingCurrencies() {
        ledger.open(account("seller:42:usd-wallet", USD, Side.CREDIT, nonNegative()));
        AccountId seller42 = new AccountId("seller:42");

        assertThatThrownBy(() -> ledger.balanceUnder(seller42))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsBranchMixingNormalBalances() {
        ledger.open(account("seller:42:fees-receivable", TRY, Side.DEBIT, unlimited()));
        AccountId seller42 = new AccountId("seller:42");

        assertThatThrownBy(() -> ledger.balanceUnder(seller42))
                .isInstanceOf(MixedNormalBalanceException.class)
                .hasMessage("Accounts under seller:42 have different normal balances");
    }

    @Test
    void rejectsNullBranch() {
        assertThatThrownBy(() -> ledger.balanceUnder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("branch");
    }

    private void pay(String orderKey, Account seller, String amount) {
        ledger.post(Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(seller, lira(amount))
                .build());
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Currency currency, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), currency, normalBalance, balancePolicy);
    }
}
