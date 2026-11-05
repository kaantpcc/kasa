package dev.kaantopcu.kasa.core.ledger;

public interface LedgerWriter {

    void post(Transaction transaction);
}
