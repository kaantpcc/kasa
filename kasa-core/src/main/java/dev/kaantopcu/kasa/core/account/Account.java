package dev.kaantopcu.kasa.core.account;

import java.util.Currency;
import java.util.Objects;

public record Account(AccountId id, Currency currency, Side normalBalance) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(normalBalance, "normalBalance");
    }
}
