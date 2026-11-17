package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrialBalanceLineTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Account BANK = account("bank", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT);

    @Test
    void readsDebitNormalBalanceAsDebitsMinusCredits() {
        TrialBalanceLine line = new TrialBalanceLine(BANK, lira("1000.00"), lira("300.00"));

        assertThat(line.balance()).isEqualTo(lira("700.00"));
    }

    @Test
    void readsCreditNormalBalanceAsCreditsMinusDebits() {
        TrialBalanceLine line = new TrialBalanceLine(SELLER_WALLET, lira("300.00"), lira("1000.00"));

        assertThat(line.balance()).isEqualTo(lira("700.00"));
    }

    @Test
    void rejectsDebitsInDifferentCurrencyThanAccount() {
        Money dollars = Money.of("100.00", USD);

        assertThatThrownBy(() -> new TrialBalanceLine(SELLER_WALLET, dollars, lira("0.00")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsCreditsInDifferentCurrencyThanAccount() {
        Money dollars = Money.of("100.00", USD);

        assertThatThrownBy(() -> new TrialBalanceLine(SELLER_WALLET, lira("0.00"), dollars))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessage("Cannot combine TRY with USD");
    }

    @Test
    void rejectsNullAccount() {
        assertThatThrownBy(() -> new TrialBalanceLine(null, lira("0.00"), lira("0.00")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }
}
