package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.HashMap;
import java.util.Map;

final class AccountRegistry {

    private final Map<AccountId, Account> accountsById = new HashMap<>();
    private final Map<AccountId, AccountStatus> statusesById = new HashMap<>();

    void open(Account account) {
        if (accountsById.containsKey(account.id())) {
            requireSameDefinition(account);
            requireNotClosed(account);
            return;
        }
        accountsById.put(account.id(), account);
        statusesById.put(account.id(), AccountStatus.OPEN);
    }

    void freeze(Account account) {
        changeStatus(account, AccountStatus.FROZEN);
    }

    void unfreeze(Account account) {
        changeStatus(account, AccountStatus.OPEN);
    }

    void close(Account account, Money balance) {
        if (statusOf(account) == AccountStatus.CLOSED) {
            return;
        }
        requireZeroBalance(account, balance);
        statusesById.put(account.id(), AccountStatus.CLOSED);
    }

    AccountStatus statusOf(Account account) {
        requireOpened(account);
        return statusesById.get(account.id());
    }

    void requireOpened(Account account) {
        if (!accountsById.containsKey(account.id())) {
            throw new AccountNotOpenException(account.id());
        }
        requireSameDefinition(account);
    }

    void requireMovementAllowed(Account account, Money balanceChange) {
        switch (statusesById.get(account.id())) {
            case OPEN -> {
            }
            case FROZEN -> requireNoOutgoingMoney(account, balanceChange);
            case CLOSED -> throw new AccountClosedException(account.id());
        }
    }

    private void changeStatus(Account account, AccountStatus target) {
        if (statusOf(account) == target) {
            return;
        }
        requireNotClosed(account);
        statusesById.put(account.id(), target);
    }

    private void requireSameDefinition(Account account) {
        if (!accountsById.get(account.id()).equals(account)) {
            throw new AccountDefinitionConflictException(account.id());
        }
    }

    private void requireNotClosed(Account account) {
        if (statusesById.get(account.id()) == AccountStatus.CLOSED) {
            throw new AccountClosedException(account.id());
        }
    }

    private static void requireNoOutgoingMoney(Account account, Money balanceChange) {
        if (balanceChange.isNegative()) {
            throw new AccountFrozenException(account.id());
        }
    }

    private static void requireZeroBalance(Account account, Money balance) {
        if (!balance.isZero()) {
            throw new NonZeroBalanceException(account.id(), balance);
        }
    }
}
