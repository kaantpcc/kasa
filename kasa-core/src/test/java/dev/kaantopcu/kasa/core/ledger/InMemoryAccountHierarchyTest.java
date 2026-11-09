package dev.kaantopcu.kasa.core.ledger;

class InMemoryAccountHierarchyTest extends AccountHierarchyContractTest {

    @Override
    protected Ledger createLedger() {
        return new InMemoryLedger();
    }
}
