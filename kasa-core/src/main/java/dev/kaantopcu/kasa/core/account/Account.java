package dev.kaantopcu.kasa.core.account;

import dev.kaantopcu.kasa.core.money.CurrencyMismatchException;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Currency;
import java.util.Objects;

/**
 * The definition of an account in the ledger.
 *
 * <p>The normal balance decides which side increases the account. Asset and expense accounts, such as
 * a bank account or customer payments, are debit-normal. Liability, equity and income accounts, such as
 * a seller's wallet or commission income, are credit-normal. The balance of an account is reported in its
 * normal direction, so a funded wallet shows a positive balance whichever side it is on.
 *
 * <p>An account must be opened in a ledger before money can be posted to it. The same id must always be
 * used with the same definition.
 *
 * @param id            the unique id of the account
 * @param currency      the only currency the account accepts
 * @param normalBalance the side that increases the account
 * @param balancePolicy how far the balance may go below zero
 */
public record Account(AccountId id, Currency currency, Side normalBalance, BalancePolicy balancePolicy) {

    /**
     * Creates an account definition.
     *
     * @param id            the unique id of the account
     * @param currency      the only currency the account accepts
     * @param normalBalance the side that increases the account
     * @param balancePolicy how far the balance may go below zero
     * @throws CurrencyMismatchException if the policy is an overdraft whose limit is in another currency
     */
    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(normalBalance, "normalBalance");
        Objects.requireNonNull(balancePolicy, "balancePolicy");
        requireLimitInAccountCurrency(currency, balancePolicy);
    }

    /**
     * Creates a debit-normal account, such as a bank account or customer payments.
     *
     * @param id            the id of the account, such as {@code "bank:main"}
     * @param currency      the only currency the account accepts
     * @param balancePolicy how far the balance may go below zero
     * @return the account definition
     * @throws IllegalArgumentException  if {@code id} is blank or has a blank segment
     * @throws CurrencyMismatchException if the policy is an overdraft whose limit is in another currency
     */
    public static Account debitNormal(String id, Currency currency, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), currency, Side.DEBIT, balancePolicy);
    }

    /**
     * Creates a credit-normal account, such as a seller's wallet or commission income.
     *
     * @param id            the id of the account, such as {@code "seller:42:wallet"}
     * @param currency      the only currency the account accepts
     * @param balancePolicy how far the balance may go below zero
     * @return the account definition
     * @throws IllegalArgumentException  if {@code id} is blank or has a blank segment
     * @throws CurrencyMismatchException if the policy is an overdraft whose limit is in another currency
     */
    public static Account creditNormal(String id, Currency currency, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), currency, Side.CREDIT, balancePolicy);
    }

    private static void requireLimitInAccountCurrency(Currency currency, BalancePolicy balancePolicy) {
        if (balancePolicy instanceof BalancePolicy.Overdraft(Money limit) && !limit.currency().equals(currency)) {
            throw new CurrencyMismatchException(currency, limit.currency());
        }
    }
}
