package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Not thread-safe on its own: InMemoryLedger calls it only while holding its read or write lock.
final class AccountRegistry {

    private final Map<AccountId, Account> accountsById = new LinkedHashMap<>();
    private final Map<AccountId, AccountStatus> statusesById = new HashMap<>();
    private final Set<AccountId> heldAccountIds = new HashSet<>();

    void open(Account account) {
        requireOpenable(account);
        if (!accountsById.containsKey(account.id())) {
            accountsById.put(account.id(), account);
            statusesById.put(account.id(), AccountStatus.OPEN);
        }
    }

    void openHoldable(HoldableAccount account) {
        List<Account> accountsToOpen = List.of(account.available(), account.held());
        accountsToOpen.forEach(this::requireOpenable);
        accountsToOpen.forEach(this::open);
        markHeld(account.held());
    }

    void markHeld(Account held) {
        heldAccountIds.add(held.id());
    }

    void requireNotHeld(Account account) {
        if (heldAccountIds.contains(account.id())) {
            throw new HeldAccountPostingException(account.id());
        }
    }

    private void requireOpenable(Account account) {
        if (accountsById.containsKey(account.id())) {
            requireSameDefinition(account);
            requireNotClosed(account);
        }
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

    List<Account> opened() {
        return List.copyOf(accountsById.values());
    }

    List<Account> accountsWithin(AccountId branch) {
        List<Account> branchAccounts = new ArrayList<>();
        for (Account account : accountsById.values()) {
            if (account.id().isWithin(branch)) {
                branchAccounts.add(account);
            }
        }
        if (branchAccounts.isEmpty()) {
            throw new AccountNotOpenException(branch);
        }
        return branchAccounts;
    }

    void requireOpened(Account account) {
        if (!accountsById.containsKey(account.id())) {
            throw new AccountNotOpenException(account.id());
        }
        requireSameDefinition(account);
    }

    void requireMovementAllowed(Account account, Money balanceChange) {
        AccountStatus status = statusesById.get(account.id());
        if (!status.acceptsMovements()) {
            throw new AccountClosedException(account.id());
        }
        if (balanceChange.isNegative() && !status.allowsOutgoingMoney()) {
            throw new AccountFrozenException(account.id());
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

    private static void requireZeroBalance(Account account, Money balance) {
        if (!balance.isZero()) {
            throw new NonZeroBalanceException(account.id(), balance);
        }
    }
}
