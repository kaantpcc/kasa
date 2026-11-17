package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class AccountHistoryTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account BANK = account("bank");
    private static final Account COUNTERPART = account("counterpart");
    private static final Instant NINE = Instant.parse("2026-11-17T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-11-17T11:00:00Z");
    private static final Instant NOON = Instant.parse("2026-11-17T12:00:00Z");

    private AccountHistory history;

    @BeforeEach
    void createHistory() {
        history = new AccountHistory(BANK);
    }

    @Test
    void startsWithZeroBalanceAndNoEntries() {
        assertThat(history.balance()).isEqualTo(Money.zero(TRY));
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void keepsBalanceAfterEachEntry() {
        record(NINE, deposit("deposit-1", "100.00"));
        record(TEN, withdrawal("withdrawal-1", "30.00"));
        record(ELEVEN, deposit("deposit-2", "50.00"));

        assertThat(history.linesBetween(NINE, NOON))
                .extracting(StatementLine::balanceAfter)
                .containsExactly(lira("100.00"), lira("70.00"), lira("120.00"));
        assertThat(history.balance()).isEqualTo(lira("120.00"));
    }

    @Test
    void leavesOutEntriesRecordedAtTheBoundaryWhenReadingBalanceBefore() {
        recordDepositsAtNineTenTenAndEleven();

        assertThat(history.balanceBefore(NINE)).isEqualTo(Money.zero(TRY));
        assertThat(history.balanceBefore(TEN)).isEqualTo(lira("100.00"));
        assertThat(history.balanceBefore(ELEVEN)).isEqualTo(lira("170.00"));
        assertThat(history.balanceBefore(NOON)).isEqualTo(lira("175.00"));
    }

    @Test
    void readsEntriesRecordedBeforeTheBoundary() {
        recordDepositsAtNineTenTenAndEleven();

        assertThat(history.entriesBefore(ELEVEN)).hasSize(3);
    }

    @Test
    void readsLinesInHalfOpenPeriod() {
        recordDepositsAtNineTenTenAndEleven();

        assertThat(history.linesBetween(TEN, ELEVEN))
                .extracting(line -> line.posted().transaction().idempotencyKey().value())
                .containsExactly("deposit-2", "deposit-3");
    }

    @Test
    void readsNoLinesInEmptyPeriod() {
        recordDepositsAtNineTenTenAndEleven();

        assertThat(history.linesBetween(TEN, TEN)).isEmpty();
    }

    private void recordDepositsAtNineTenTenAndEleven() {
        record(NINE, deposit("deposit-1", "100.00"));
        record(TEN, deposit("deposit-2", "50.00"));
        record(TEN, deposit("deposit-3", "20.00"));
        record(ELEVEN, deposit("deposit-4", "5.00"));
    }

    private void record(Instant recordedAt, Transaction transaction) {
        PostedTransaction posted = new PostedTransaction(transaction, recordedAt,
                LocalDate.ofInstant(recordedAt, ZoneOffset.UTC), Hash.GENESIS, Hash.GENESIS);
        history.record(posted, transaction.entries().getFirst());
    }

    private static Transaction deposit(String key, String amount) {
        return Transaction.builder(key)
                .debit(BANK, lira(amount))
                .credit(COUNTERPART, lira(amount))
                .build();
    }

    private static Transaction withdrawal(String key, String amount) {
        return Transaction.builder(key)
                .credit(BANK, lira(amount))
                .debit(COUNTERPART, lira(amount))
                .build();
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id) {
        return new Account(new AccountId(id), TRY, Side.DEBIT, BalancePolicy.unlimited());
    }
}
