package dev.kaantopcu.kasa.core.ledger;

import java.util.List;
import java.util.Objects;

/**
 * The unique key of a transaction or hold, chosen by the caller.
 *
 * <p>Use a key that identifies the business event, such as an order or payment id. If the same request is
 * retried with the same key and the same transaction, the ledger records it only once. Methods that take a
 * key also have a {@code String} shortcut, for example {@code ledger.reverse("order-1")}.
 *
 * <p>The ledger names the transactions it creates itself by adding a suffix to the caller's key:
 * {@code ":reversal"}, {@code ":capture"} and {@code ":release"}. These suffixes are reserved, so
 * {@link LedgerWriter#post(Transaction)} and {@link LedgerWriter#placeHold(Hold)} reject keys that end
 * with one of them.
 *
 * @param value the key text; not blank
 */
public record IdempotencyKey(String value) {

    static final String REVERSAL_SUFFIX = ":reversal";
    static final String CAPTURE_SUFFIX = ":capture";
    static final String RELEASE_SUFFIX = ":release";
    private static final List<String> RESERVED_SUFFIXES = List.of(REVERSAL_SUFFIX, CAPTURE_SUFFIX, RELEASE_SUFFIX);

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

    IdempotencyKey withSuffix(String suffix) {
        return new IdempotencyKey(value + suffix);
    }

    void requireNotReversal() {
        if (value.endsWith(REVERSAL_SUFFIX)) {
            throw new IllegalArgumentException(
                    "Transaction %s is a reversal and cannot be reversed; post the original again with a new key"
                            .formatted(value));
        }
    }

    void requireNotReserved() {
        for (String suffix : RESERVED_SUFFIXES) {
            if (value.endsWith(suffix)) {
                throw new IllegalArgumentException(
                        "Idempotency key %s ends with %s, which is reserved for transactions the ledger creates"
                                .formatted(value, suffix));
            }
        }
    }
}
