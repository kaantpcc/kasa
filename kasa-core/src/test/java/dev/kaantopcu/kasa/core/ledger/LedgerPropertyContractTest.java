package dev.kaantopcu.kasa.core.ledger;

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

    protected abstract Ledger createLedger(Clock clock);

    static LongStream seeds() {
        return LongStream.rangeClosed(1, LAST_SEED);
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void keepsInvariantsAfterEveryOperation(long seed) {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);
        Ledger ledger = createLedger(clock);
        Marketplace.openAll(ledger);
        OperationGenerator generator = new OperationGenerator(new Random(seed), clock);
        List<String> history = new ArrayList<>();

        for (int step = 1; step <= OPERATIONS_PER_RUN; step++) {
            LedgerOperation operation = generator.next(step);
            try {
                apply(operation, ledger, history);
                checkInvariants(ledger, clock.instant().plus(JUST_AFTER));
            } catch (RuntimeException | AssertionError failure) {
                throw new AssertionError(failureReport(seed, history), failure);
            }
        }
    }

    private static void apply(LedgerOperation operation, Ledger ledger, List<String> history) {
        history.add(operation.description());
        try {
            operation.applyTo(ledger);
        } catch (LedgerException rejected) {
            history.add("    rejected: " + rejected.getMessage());
        }
    }

    private static void checkInvariants(Ledger ledger, Instant afterLastRecord) {
        assertThat(ledger.trialBalance(afterLastRecord).isBalanced())
                .as("trial balance is balanced in every currency")
                .isTrue();
        assertThat(ledger.verifyChain())
                .as("hash chain is intact")
                .isEmpty();
    }

    private static String failureReport(long seed, List<String> history) {
        return "Invariant broken with seed %d after these operations:%n%s"
                .formatted(seed, String.join(System.lineSeparator(), history));
    }
}
