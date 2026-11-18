package dev.kaantopcu.kasa.core.ledger;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A SHA-256 hash in the ledger's audit chain, as 64 lowercase hexadecimal characters.
 *
 * <p>Each posted transaction's hash covers its content and the hash of the transaction before it, so
 * changing any recorded transaction breaks every link after it. See {@link LedgerReader#verifyChain()}.
 *
 * @param value the 64 lowercase hexadecimal characters
 */
public record Hash(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    /**
     * The hash that comes before the first transaction: 64 zeros.
     */
    public static final Hash GENESIS = new Hash("0".repeat(64));

    /**
     * Creates a hash from its hexadecimal text.
     *
     * @param value the 64 lowercase hexadecimal characters
     * @throws IllegalArgumentException if {@code value} is not 64 lowercase hexadecimal characters
     */
    public Hash {
        Objects.requireNonNull(value, "value");
        if (!SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Hash must be 64 lowercase hex characters, got %s".formatted(value));
        }
    }
}
