package dev.kaantopcu.kasa.benchmarks;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.ledger.Entry;
import dev.kaantopcu.kasa.core.ledger.IdempotencyKey;
import dev.kaantopcu.kasa.core.ledger.Ledger;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class ReadBenchmark {

    @Param({"1000", "10000", "100000"})
    public int transactions;

    private Ledger ledger;
    private Account wallet;
    private Instant midpoint;

    @Setup
    public void createLedger() {
        List<Account> wallets = LedgerFixture.wallets();
        ledger = LedgerFixture.ledgerWithSales(wallets, transactions);
        wallet = wallets.getFirst();
        midpoint = LedgerFixture.recordedAtOfSale(ledger, transactions / 2);
    }

    @Benchmark
    public Money balance() {
        return ledger.balance(wallet);
    }

    @Benchmark
    public Money balanceAsOf() {
        return ledger.balanceAsOf(wallet, midpoint);
    }

    @Benchmark
    public List<Entry> entries() {
        return ledger.entries(wallet);
    }

    @Benchmark
    public Optional<IdempotencyKey> verifyChain() {
        return ledger.verifyChain();
    }
}
