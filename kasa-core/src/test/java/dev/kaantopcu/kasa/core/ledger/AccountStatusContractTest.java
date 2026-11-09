package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class AccountStatusContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account SELLER_WALLET = account("seller-42-wallet", Side.CREDIT, nonNegative());

    private Ledger ledger;

    protected abstract Ledger createLedger();

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        ledger = createLedger();
        ledger.open(SELLER_WALLET);
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

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }
}
