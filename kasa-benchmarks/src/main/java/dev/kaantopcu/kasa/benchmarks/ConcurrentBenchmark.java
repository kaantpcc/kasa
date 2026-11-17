package dev.kaantopcu.kasa.benchmarks;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.ledger.Ledger;
import dev.kaantopcu.kasa.core.money.Money;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class ConcurrentBenchmark {

    @Param({"1000", "10000", "100000"})
    public int transactions;

    private final AtomicInteger nextSale = new AtomicInteger();
    private List<Account> wallets;
    private Ledger ledger;

    @Setup(Level.Iteration)
    public void createLedger() {
        wallets = LedgerFixture.wallets();
        ledger = LedgerFixture.ledgerWithSales(wallets, transactions);
        nextSale.set(0);
    }

    @Benchmark
    @Group("readsDuringWrites")
    @GroupThreads(3)
    public Money read() {
        return ledger.balance(wallets.get(ThreadLocalRandom.current().nextInt(wallets.size())));
    }

    @Benchmark
    @Group("readsDuringWrites")
    @GroupThreads(1)
    public void write() {
        int sale = nextSale.getAndIncrement();
        ledger.post(LedgerFixture.sale("concurrent-" + sale, wallets.get(sale % wallets.size())));
    }
}
