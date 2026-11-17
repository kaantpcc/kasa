package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Currency;
import java.util.Objects;

public record Account(AccountId id, Currency currency, Side normalBalance, BalancePolicy balancePolicy) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(normalBalance, "normalBalance");
        Objects.requireNonNull(balancePolicy, "balancePolicy");
        requireLimitInAccountCurrency(currency, balancePolicy);
    }

    public static Account debitNormal(String id, Currency currency, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), currency, Side.DEBIT, balancePolicy);
    }

    public static Account creditNormal(String id, Currency currency, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), currency, Side.CREDIT, balancePolicy);
    }

    private static void requireLimitInAccountCurrency(Currency currency, BalancePolicy balancePolicy) {
        if (balancePolicy instanceof BalancePolicy.Overdraft(Money limit) && !limit.currency().equals(currency)) {
            throw new CurrencyMismatchException(currency, limit.currency());
        }
    }
}
