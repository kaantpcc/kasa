package dev.kaantopcu.kasa.core.ledger;

import java.time.Clock;

class InMemoryLedgerPropertyTest extends LedgerPropertyContractTest {

    @Override
    protected Ledger createLedger(Clock clock) {
        return new InMemoryLedger(clock);
    }
}
