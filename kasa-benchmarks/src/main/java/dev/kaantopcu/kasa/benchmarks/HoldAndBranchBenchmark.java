package dev.kaantopcu.kasa.benchmarks;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.ledger.Hold;
import dev.kaantopcu.kasa.core.ledger.IdempotencyKey;
import dev.kaantopcu.kasa.core.ledger.InMemoryLedger;
import dev.kaantopcu.kasa.core.ledger.Ledger;
import dev.kaantopcu.kasa.core.ledger.Transaction;
import dev.kaantopcu.kasa.core.money.Money;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class HoldAndBranchBenchmark {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account BANK = Account.debitNormal("bank", TRY, BalancePolicy.unlimited());
    private static final HoldableAccount HOLDER = new HoldableAccount(
            Account.creditNormal("holder:wallet", TRY, BalancePolicy.nonNegative()),
            Account.creditNormal("holder:held", TRY, BalancePolicy.nonNegative()));
    private static final Money HOLD_AMOUNT = Money.of("1.00", TRY);
    private static final AccountId ONE_SELLER = new AccountId("seller:7");

    @Param({"1000", "10000", "100000"})
    public int size;

    private Ledger ledger;

    @Setup
    public void createLedger() {
        ledger = new InMemoryLedger(Clock.systemUTC());
        ledger.open(BANK);
        for (int seller = 0; seller < size; seller++) {
            ledger.open(Account.creditNormal("seller:" + seller + ":wallet", TRY, BalancePolicy.nonNegative()));
        }
        ledger.openHoldable(HOLDER);
        Money funds = HOLD_AMOUNT.multiply(BigDecimal.valueOf(size + 1L), RoundingMode.UNNECESSARY);
        ledger.post(Transaction.builder("fund-holder").debit(BANK, funds).credit(HOLDER.available(), funds).build());
        for (int hold = 0; hold < size; hold++) {
            IdempotencyKey key = new IdempotencyKey("settled-hold-" + hold);
            ledger.placeHold(new Hold(key, HOLDER, HOLD_AMOUNT));
            ledger.releaseHold(key);
        }
        ledger.placeHold(new Hold(new IdempotencyKey("active-hold"), HOLDER, HOLD_AMOUNT));
    }

    @Benchmark
    public Money balanceUnder() {
        return ledger.balanceUnder(ONE_SELLER);
    }

    @Benchmark
    public List<Hold> activeHolds() {
        return ledger.activeHolds(HOLDER);
    }
}
