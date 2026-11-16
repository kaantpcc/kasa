package dev.kaantopcu.kasa.core.ledger;

import java.util.Objects;
import java.util.regex.Pattern;

public record Hash(String value) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    public static final Hash GENESIS = new Hash("0".repeat(64));

    public Hash {
        Objects.requireNonNull(value, "value");
        if (!SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Hash must be 64 lowercase hex characters, got %s".formatted(value));
        }
    }
}
