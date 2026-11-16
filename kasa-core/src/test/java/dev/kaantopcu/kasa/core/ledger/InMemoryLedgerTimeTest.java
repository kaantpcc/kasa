package dev.kaantopcu.kasa.core.ledger;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerTimeTest extends LedgerTimeContractTest {

    @Override
    protected Ledger createLedger(Clock clock) {
        return new InMemoryLedger(clock);
    }

    @Test
    void rejectsNullClock() {
        assertThatThrownBy(() -> new InMemoryLedger(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
    }
}
