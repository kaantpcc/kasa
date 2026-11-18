package dev.kaantopcu.kasa.core.ledger;

import java.util.Objects;

/**
 * The unique key of a transaction or hold, chosen by the caller.
 *
 * <p>Use a key that identifies the business event, such as an order or payment id. If the same request is
 * retried with the same key and the same transaction, the ledger records it only once. Methods that take a
 * key also have a {@code String} shortcut, for example {@code ledger.reverse("order-1")}.
 *
 * <p>The ledger derives keys for the transactions it creates itself by adding a suffix:
 * {@code ":reversal"}, {@code ":capture"} and {@code ":release"}.
 *
 * @param value the key text; not blank
 */
public record IdempotencyKey(String value) {

    /**
     * Creates an idempotency key.
     *
     * @param value the key text, such as {@code "order-1"}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
    }
}
