package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

final class LedgerInvariants {

    private final Ledger ledger;
    private final Instant start;

    LedgerInvariants(Ledger ledger, Instant start) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.start = Objects.requireNonNull(start, "start");
    }

    void checkAll(Instant afterLastRecord) {
        checkTrialBalance(afterLastRecord);
        checkHashChain();
        checkBalancePolicies();
        checkQueriesAgree(afterLastRecord);
        checkRefundsStayWithinOriginals(afterLastRecord);
    }

    private void checkTrialBalance(Instant afterLastRecord) {
        assertThat(ledger.trialBalance(afterLastRecord).isBalanced())
                .as("trial balance is balanced in every currency")
                .isTrue();
    }

    private void checkHashChain() {
        assertThat(ledger.verifyChain())
                .as("hash chain is intact")
                .isEmpty();
    }

    private void checkBalancePolicies() {
        for (Account account : Marketplace.ALL) {
            Money balance = ledger.balance(account);
            assertThat(isWithinPolicy(account.balancePolicy(), balance))
                    .as("%s balance %s stays within %s", idOf(account), balance.amount().toPlainString(),
                            account.balancePolicy())
                    .isTrue();
        }
    }

    private void checkQueriesAgree(Instant afterLastRecord) {
        for (Account account : Marketplace.ALL) {
            Money balance = ledger.balance(account);
            assertThat(sumOf(account, ledger.entries(account)))
                    .as("%s balance equals the sum of its entries", idOf(account))
                    .isEqualTo(balance);
            assertThat(ledger.balanceAsOf(account, afterLastRecord))
                    .as("%s balance as of now equals its balance", idOf(account))
                    .isEqualTo(balance);
            assertThat(ledger.statement(account, start, afterLastRecord).closingBalance())
                    .as("%s statement closes at its balance", idOf(account))
                    .isEqualTo(balance);
        }
        assertThat(ledger.balanceUnder(Marketplace.SELLERS))
                .as("seller branch balance equals the sum of seller account balances")
                .isEqualTo(sumOfBalances(Marketplace.SELLER_ACCOUNTS));
    }

    private void checkRefundsStayWithinOriginals(Instant afterLastRecord) {
        for (Account account : Marketplace.ALL) {
            List<StatementLine> lines = ledger.statement(account, start, afterLastRecord).lines();
            Map<IdempotencyKey, Money> originals = changesBy(lines,
                    line -> Optional.of(line.posted().transaction().idempotencyKey()));
            Map<IdempotencyKey, Money> refunds = changesBy(lines, line -> line.posted().transaction().refundOf());
            refunds.forEach((originalKey, refunded) -> {
                Money original = originals.getOrDefault(originalKey, Money.zero(account.currency()));
                assertThat(isBetweenZeroAnd(original.add(refunded), original))
                        .as("refunds of %s on %s stay within the original", originalKey.value(), idOf(account))
                        .isTrue();
            });
        }
    }

    private Money sumOfBalances(List<Account> accounts) {
        Money total = Money.zero(accounts.getFirst().currency());
        for (Account account : accounts) {
            total = total.add(ledger.balance(account));
        }
        return total;
    }

    private static Map<IdempotencyKey, Money> changesBy(List<StatementLine> lines,
                                                        Function<StatementLine, Optional<IdempotencyKey>> keyOf) {
        Map<IdempotencyKey, Money> changes = new LinkedHashMap<>();
        for (StatementLine line : lines) {
            keyOf.apply(line).ifPresent(key -> changes.merge(key, line.entry().balanceChange(), Money::add));
        }
        return changes;
    }

    private static Money sumOf(Account account, List<Entry> entries) {
        Money total = Money.zero(account.currency());
        for (Entry entry : entries) {
            total = total.add(entry.balanceChange());
        }
        return total;
    }

    private static boolean isWithinPolicy(BalancePolicy policy, Money balance) {
        return switch (policy) {
            case BalancePolicy.Unlimited() -> true;
            case BalancePolicy.NonNegative() -> !balance.isNegative();
            case BalancePolicy.Overdraft(Money limit) -> !balance.add(limit).isNegative();
        };
    }

    private static boolean isBetweenZeroAnd(Money value, Money bound) {
        if (bound.isNegative()) {
            return !value.isPositive() && !value.isLessThan(bound);
        }
        return !value.isNegative() && !value.isGreaterThan(bound);
    }

    private static String idOf(Account account) {
        return account.id().value();
    }
}
