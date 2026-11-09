package dev.kaantopcu.kasa.core.ledger;

class InMemoryLedgerTest extends LedgerContractTest {

    @Override
    protected Ledger createLedger() {
        return new InMemoryLedger();
    }
}
