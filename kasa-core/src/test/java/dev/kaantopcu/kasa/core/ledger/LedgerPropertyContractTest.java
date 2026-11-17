package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

abstract class LedgerPropertyContractTest {

    private static final Instant START = Instant.parse("2026-11-17T09:00:00Z");
    private static final int OPERATIONS_PER_RUN = 50;
    private static final long LAST_SEED = 200;
    private static final Duration JUST_AFTER = Duration.ofNanos(1);

    private final List<String> history = new ArrayList<>();
    private AdjustableClock clock;
    private Ledger ledger;
    private LedgerInvariants invariants;

    protected abstract Ledger createLedger(Clock clock);

    static LongStream seeds() {
        return LongStream.rangeClosed(1, LAST_SEED);
    }

    @BeforeEach
    void openMarketplace() {
        clock = new AdjustableClock(START, ZoneOffset.UTC);
        ledger = createLedger(clock);
        Marketplace.openAll(ledger);
        invariants = new LedgerInvariants(ledger, START);
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void keepsInvariantsAfterEveryOperation(long seed) {
        OperationGenerator generator = new OperationGenerator(new Random(seed), clock);

        for (int step = 1; step <= OPERATIONS_PER_RUN; step++) {
            LedgerOperation operation = generator.next(step);
            try {
                apply(operation);
                invariants.checkAll(afterLastRecord());
            } catch (RuntimeException | AssertionError failure) {
                throw new AssertionError(failureReport(seed), failure);
            }
        }
    }

    private void apply(LedgerOperation operation) {
        history.add(operation.description());
        Snapshot before = snapshot();
        try {
            operation.applyTo(ledger);
        } catch (LedgerException rejected) {
            history.add("    rejected: " + rejected.getMessage());
            assertThat(snapshot())
                    .as("rejected operation leaves the ledger unchanged")
                    .isEqualTo(before);
        }
    }

    private Snapshot snapshot() {
        List<AccountStatus> statuses = Marketplace.ALL.stream()
                .map(ledger::status)
                .toList();
        List<Hold> activeHolds = Marketplace.HOLDABLE_SELLERS.stream()
                .flatMap(seller -> ledger.activeHolds(seller).stream())
                .toList();
        return new Snapshot(ledger.trialBalance(afterLastRecord()), statuses, activeHolds);
    }

    private Instant afterLastRecord() {
        return clock.instant().plus(JUST_AFTER);
    }

    private String failureReport(long seed) {
        return "Invariant broken with seed %d after these operations:%n%s"
                .formatted(seed, String.join(System.lineSeparator(), history));
    }

    private record Snapshot(TrialBalance trialBalance, List<AccountStatus> statuses, List<Hold> activeHolds) {
    }
}
