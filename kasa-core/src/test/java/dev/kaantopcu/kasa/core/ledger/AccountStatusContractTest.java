package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.unlimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class AccountStatusContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT, unlimited());
    private static final Account BANK = account("bank", Side.DEBIT, unlimited());
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT, nonNegative());
    private static final HoldableAccount SELLER = new HoldableAccount(
            SELLER_WALLET,
            account("seller-42-wallet-held", Side.CREDIT, nonNegative()));
    private static final IdempotencyKey HOLD_KEY = new IdempotencyKey("payout-1-hold");

    private Ledger ledger;

    protected abstract Ledger createLedger();

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        ledger = createLedger();
        ledger.open(CUSTOMER_PAYMENTS);
        ledger.open(BANK);
        ledger.open(SELLER_WALLET);
        ledger.open(SELLER.held());
    }

    @Test
    void newlyOpenedAccountIsOpen() {
        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void freezeMarksAccountFrozen() {
        ledger.freeze(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void unfreezeReturnsFrozenAccountToOpen() {
        ledger.freeze(SELLER_WALLET);

        ledger.unfreeze(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void closeMarksOpenAccountClosed() {
        ledger.close(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void closeMarksFrozenAccountClosed() {
        ledger.freeze(SELLER_WALLET);

        ledger.close(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void ignoresFreezingFrozenAccount() {
        ledger.freeze(SELLER_WALLET);

        ledger.freeze(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void ignoresUnfreezingOpenAccount() {
        ledger.unfreeze(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void ignoresClosingClosedAccount() {
        ledger.close(SELLER_WALLET);

        ledger.close(SELLER_WALLET);

        assertThat(ledger.status(SELLER_WALLET)).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void rejectsFreezingClosedAccount() {
        ledger.close(SELLER_WALLET);

        assertThatThrownBy(() -> ledger.freeze(SELLER_WALLET))
                .isInstanceOf(AccountClosedException.class)
                .hasMessage("Account seller-42-wallet is closed");
    }

    @Test
    void rejectsUnfreezingClosedAccount() {
        ledger.close(SELLER_WALLET);

        assertThatThrownBy(() -> ledger.unfreeze(SELLER_WALLET))
                .isInstanceOf(AccountClosedException.class)
                .hasMessage("Account seller-42-wallet is closed");
    }

    @Test
    void rejectsStatusChangeOfAccountThatIsNotOpened() {
        Account misspelledWallet = account("seler-42-wallet", Side.CREDIT, nonNegative());

        assertThatThrownBy(() -> ledger.freeze(misspelledWallet))
                .isInstanceOf(AccountNotOpenException.class)
                .hasMessage("Account seler-42-wallet has not been opened");
    }

    @Test
    void rejectsStatusOfAccountThatIsNotOpened() {
        Account misspelledWallet = account("seler-42-wallet", Side.CREDIT, nonNegative());

        assertThatThrownBy(() -> ledger.status(misspelledWallet))
                .isInstanceOf(AccountNotOpenException.class)
                .hasMessage("Account seler-42-wallet has not been opened");
    }

    @Test
    void rejectsNullAccountWhenFreezing() {
        assertThatThrownBy(() -> ledger.freeze(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void rejectsNullAccountWhenUnfreezing() {
        assertThatThrownBy(() -> ledger.unfreeze(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void rejectsNullAccountWhenClosing() {
        assertThatThrownBy(() -> ledger.close(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void rejectsNullAccountForStatus() {
        assertThatThrownBy(() -> ledger.status(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void acceptsIncomingMoneyToFrozenAccount() {
        ledger.freeze(SELLER_WALLET);

        ledger.post(sale("order-1", "900.00"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void rejectsPayoutFromFrozenAccount() {
        ledger.post(sale("order-1", "900.00"));
        ledger.freeze(SELLER_WALLET);
        Transaction payout = payout("payout-1", "300.00");

        assertThatThrownBy(() -> ledger.post(payout))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessage("Account seller-42-wallet is frozen");
    }

    @Test
    void writesNothingWhenPayoutFromFrozenAccountIsRejected() {
        ledger.post(sale("order-1", "900.00"));
        ledger.freeze(SELLER_WALLET);
        Transaction payout = payout("payout-1", "300.00");

        assertThatThrownBy(() -> ledger.post(payout))
                .isInstanceOf(AccountFrozenException.class);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
        assertThat(ledger.balance(BANK)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void reportsFrozenAccountBeforeInsufficientFunds() {
        ledger.freeze(SELLER_WALLET);
        Transaction payout = payout("payout-1", "300.00");

        assertThatThrownBy(() -> ledger.post(payout))
                .isInstanceOf(AccountFrozenException.class);
    }

    @Test
    void rejectsHoldOnFrozenAccount() {
        ledger.post(sale("order-1", "900.00"));
        ledger.freeze(SELLER_WALLET);
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));

        assertThatThrownBy(() -> ledger.placeHold(hold))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessage("Account seller-42-wallet is frozen");
    }

    @Test
    void rejectsReversalThatTakesMoneyFromFrozenAccount() {
        ledger.post(sale("order-1", "900.00"));
        ledger.freeze(SELLER_WALLET);

        assertThatThrownBy(() -> ledger.reverse(new IdempotencyKey("order-1")))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessage("Account seller-42-wallet is frozen");
    }

    @Test
    void capturesHoldPlacedBeforeFreeze() {
        ledger.post(sale("order-1", "900.00"));
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        ledger.placeHold(hold);
        ledger.freeze(SELLER_WALLET);

        ledger.post(hold.capture(BANK));

        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    @Test
    void releasesHoldBackIntoFrozenAccount() {
        ledger.post(sale("order-1", "900.00"));
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        ledger.placeHold(hold);
        ledger.freeze(SELLER_WALLET);

        ledger.post(hold.release());

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void acceptsPayoutAfterUnfreeze() {
        ledger.post(sale("order-1", "900.00"));
        ledger.freeze(SELLER_WALLET);
        ledger.unfreeze(SELLER_WALLET);

        ledger.post(payout("payout-1", "300.00"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    @Test
    void ignoresRetryOfPayoutAcceptedBeforeFreeze() {
        ledger.post(sale("order-1", "900.00"));
        ledger.post(payout("payout-1", "300.00"));
        ledger.freeze(SELLER_WALLET);

        ledger.post(payout("payout-1", "300.00"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    private static Transaction sale(String orderKey, String amount) {
        return Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(SELLER_WALLET, lira(amount))
                .build();
    }

    private static Transaction payout(String key, String amount) {
        return Transaction.builder(key)
                .debit(SELLER_WALLET, lira(amount))
                .credit(BANK, lira(amount))
                .build();
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }
}
