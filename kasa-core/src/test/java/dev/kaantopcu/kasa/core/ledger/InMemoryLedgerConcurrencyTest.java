package dev.kaantopcu.kasa.core.ledger;

class InMemoryLedgerConcurrencyTest extends LedgerConcurrencyContractTest {

    @Override
    protected Ledger createLedger() {
        return new InMemoryLedger();
    }
}
