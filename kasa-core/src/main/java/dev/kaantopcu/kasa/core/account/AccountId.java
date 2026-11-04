package dev.kaantopcu.kasa.core.account;

import java.util.Objects;

public record AccountId(String value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
    }
}
