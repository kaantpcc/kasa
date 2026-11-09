package dev.kaantopcu.kasa.core.ledger;

class InMemoryAccountStatusTest extends AccountStatusContractTest {

    @Override
    protected Ledger createLedger() {
        return new InMemoryLedger();
    }
}
