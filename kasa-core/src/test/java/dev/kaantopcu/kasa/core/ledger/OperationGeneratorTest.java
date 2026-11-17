package dev.kaantopcu.kasa.core.ledger;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class OperationGeneratorTest {

    private static final Instant START = Instant.parse("2026-11-17T09:00:00Z");
    private static final int STEPS = 50;

    @Test
    void producesSameOperationsForSameSeed() {
        assertThat(descriptionsFor(42)).isEqualTo(descriptionsFor(42));
    }

    @Test
    void producesDifferentOperationsForDifferentSeeds() {
        assertThat(descriptionsFor(1)).isNotEqualTo(descriptionsFor(2));
    }

    private static List<String> descriptionsFor(long seed) {
        OperationGenerator generator = new OperationGenerator(new Random(seed), new AdjustableClock(START, ZoneOffset.UTC));
        List<String> descriptions = new ArrayList<>();
        for (int step = 1; step <= STEPS; step++) {
            descriptions.add(generator.next(step).description());
        }
        return descriptions;
    }
}
