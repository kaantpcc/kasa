package dev.kaantopcu.kasa.core.ledger;

/**
 * A complete ledger: account administration, recording and reading.
 *
 * <p>Depend on the smaller interfaces where you can: {@link LedgerReader} for code that only reads,
 * {@link LedgerWriter} for code that records money, {@link AccountAdministration} for code that manages
 * accounts.
 */
public interface Ledger extends AccountAdministration, LedgerWriter, LedgerReader {
}
