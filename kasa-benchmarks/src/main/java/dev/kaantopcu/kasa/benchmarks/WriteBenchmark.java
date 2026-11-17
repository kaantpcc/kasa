package dev.kaantopcu.kasa.benchmarks;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.ledger.Ledger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class WriteBenchmark {

    private static final int POSTS_PER_ITERATION = 1_000;

    @Param({"1000", "10000", "100000"})
    public int transactions;

    private List<Account> wallets;
    private Ledger ledger;

    @Setup(Level.Iteration)
    public void createLedger() {
        wallets = LedgerFixture.wallets();
        ledger = LedgerFixture.ledgerWithSales(wallets, transactions);
    }

    @Benchmark
    @OperationsPerInvocation(POSTS_PER_ITERATION)
    public void postIncoming() {
        for (int post = 0; post < POSTS_PER_ITERATION; post++) {
            ledger.post(LedgerFixture.sale("incoming-" + post, walletFor(post)));
        }
    }

    @Benchmark
    @OperationsPerInvocation(POSTS_PER_ITERATION)
    public void postOutgoing() {
        for (int post = 0; post < POSTS_PER_ITERATION; post++) {
            ledger.post(LedgerFixture.payout("outgoing-" + post, walletFor(post)));
        }
    }

    private Account walletFor(int post) {
        return wallets.get(post % wallets.size());
    }
}
