package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT);

    @Test
    void acceptsClosingBalanceEqualToLastLine() {
        Statement statement = new Statement(lira("0.00"), List.of(saleLine()), lira("900.00"));

        assertThat(statement.closingBalance()).isEqualTo(lira("900.00"));
    }

    @Test
    void rejectsClosingBalanceThatDoesNotMatchLastLine() {
        List<StatementLine> lines = List.of(saleLine());

        assertThatThrownBy(() -> new Statement(lira("0.00"), lines, lira("800.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Closing balance 800.00 TRY does not match the statement lines");
    }

    @Test
    void rejectsClosingBalanceDifferentFromOpeningWhenThereAreNoLines() {
        assertThatThrownBy(() -> new Statement(lira("900.00"), List.of(), lira("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Closing balance 0.00 TRY does not match the statement lines");
    }

    private static StatementLine saleLine() {
        Transaction sale = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                .credit(SELLER_WALLET, lira("900.00"))
                .build();
        PostedTransaction posted = new PostedTransaction(sale, Instant.parse("2026-11-16T09:00:00Z"), LocalDate.of(2026, 11, 16),
                Hash.GENESIS, Hash.GENESIS);
        return new StatementLine(posted, new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT), lira("900.00"));
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }
}
