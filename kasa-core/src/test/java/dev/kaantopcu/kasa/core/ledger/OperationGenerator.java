package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.money.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.StringJoiner;

final class OperationGenerator {

    private static final int MAX_SALE_CENTS = 100_000;
    private static final int MAX_SPEND_CENTS = 30_000;
    private static final int MAX_FEE_CENTS = 5_000;
    private static final int MAX_CLOCK_STEP_MINUTES = 180;
    private static final List<Kind> KINDS = List.of(
            Kind.SALE, Kind.SALE, Kind.SALE,
            Kind.PAYOUT, Kind.FEE, Kind.TRANSFER, Kind.REFUND, Kind.REVERSAL,
            Kind.PLACE_HOLD, Kind.PLACE_HOLD, Kind.CAPTURE_HOLD, Kind.RELEASE_HOLD,
            Kind.FREEZE, Kind.UNFREEZE, Kind.UNFREEZE,
            Kind.RETRY, Kind.ADVANCE_CLOCK);

    private final Random random;
    private final AdjustableClock clock;
    private final List<Sale> sales = new ArrayList<>();
    private final List<Hold> holds = new ArrayList<>();
    private final List<IdempotencyKey> transactionKeys = new ArrayList<>();
    private final List<LedgerOperation> generated = new ArrayList<>();

    OperationGenerator(Random random, AdjustableClock clock) {
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    LedgerOperation next(int step) {
        String key = "op-" + step;
        LedgerOperation operation = switch (pick(KINDS)) {
            case SALE -> sale(key);
            case PAYOUT -> payout(key);
            case FEE -> fee(key);
            case TRANSFER -> transfer(key);
            case REFUND -> refund(key);
            case REVERSAL -> reversal(key);
            case PLACE_HOLD -> placeHold(key);
            case CAPTURE_HOLD -> captureHold(key);
            case RELEASE_HOLD -> releaseHold(key);
            case FREEZE -> freeze(key);
            case UNFREEZE -> unfreeze(key);
            case RETRY -> retry(key);
            case ADVANCE_CLOCK -> advanceClock(key);
        };
        generated.add(operation);
        return operation;
    }

    private LedgerOperation sale(String key) {
        Account wallet = pick(Marketplace.WALLETS);
        Money amount = amountUpTo(wallet.currency(), MAX_SALE_CENTS);
        sales.add(new Sale(key, wallet, amount));
        return post("sale", Transaction.builder(key)
                .debit(Marketplace.bankFor(wallet), amount)
                .credit(wallet, amount)
                .build());
    }

    private LedgerOperation payout(String key) {
        Account wallet = pick(Marketplace.WALLETS);
        Money amount = amountUpTo(wallet.currency(), MAX_SPEND_CENTS);
        return post("payout", Transaction.builder(key)
                .debit(wallet, amount)
                .credit(Marketplace.bankFor(wallet), amount)
                .build());
    }

    private LedgerOperation fee(String key) {
        Account wallet = pick(Marketplace.LIRA_WALLETS);
        Money amount = amountUpTo(wallet.currency(), MAX_FEE_CENTS);
        return post("fee", Transaction.builder(key)
                .debit(wallet, amount)
                .credit(Marketplace.FEES, amount)
                .build());
    }

    private LedgerOperation transfer(String key) {
        boolean firstToSecond = random.nextBoolean();
        Account from = firstToSecond ? Marketplace.FIRST_WALLET : Marketplace.SECOND_WALLET;
        Account to = firstToSecond ? Marketplace.SECOND_WALLET : Marketplace.FIRST_WALLET;
        Money amount = amountUpTo(from.currency(), MAX_SPEND_CENTS);
        return post("transfer", Transaction.builder(key)
                .debit(from, amount)
                .credit(to, amount)
                .build());
    }

    private LedgerOperation refund(String key) {
        if (sales.isEmpty()) {
            return sale(key);
        }
        Sale sale = pick(sales);
        Money amount = amountUpTo(sale.amount().currency(), centsOf(sale.amount()) * 3 / 2);
        return post("refund of " + sale.key(), Transaction.builder(key)
                .refundOf(sale.key())
                .debit(sale.wallet(), amount)
                .credit(Marketplace.bankFor(sale.wallet()), amount)
                .build());
    }

    private LedgerOperation reversal(String key) {
        if (transactionKeys.isEmpty()) {
            return sale(key);
        }
        IdempotencyKey original = pick(transactionKeys);
        return new LedgerOperation("%s reverse %s".formatted(key, original.value()),
                ledger -> ledger.reverse(original));
    }

    private LedgerOperation placeHold(String key) {
        HoldableAccount seller = pick(Marketplace.HOLDABLE_SELLERS);
        Hold hold = new Hold(new IdempotencyKey(key), seller, amountUpTo(Marketplace.TRY, MAX_SPEND_CENTS));
        holds.add(hold);
        transactionKeys.add(hold.key());
        return new LedgerOperation("%s place hold %s on %s".formatted(key, textOf(hold.amount()), idOf(seller.available())),
                ledger -> ledger.placeHold(hold));
    }

    private LedgerOperation captureHold(String key) {
        if (holds.isEmpty()) {
            return placeHold(key);
        }
        Hold hold = pick(holds);
        Money amount = amountUpTo(Marketplace.TRY, centsOf(hold.amount()));
        Account destination = pick(Marketplace.CAPTURE_DESTINATIONS);
        transactionKeys.add(hold.captureKey());
        return new LedgerOperation("%s capture %s of hold %s to %s".formatted(
                key, textOf(amount), hold.key().value(), idOf(destination)),
                ledger -> ledger.captureHold(hold.key(), destination, amount));
    }

    private LedgerOperation releaseHold(String key) {
        if (holds.isEmpty()) {
            return placeHold(key);
        }
        Hold hold = pick(holds);
        transactionKeys.add(hold.releaseKey());
        return new LedgerOperation("%s release hold %s".formatted(key, hold.key().value()),
                ledger -> ledger.releaseHold(hold.key()));
    }

    private LedgerOperation freeze(String key) {
        Account wallet = pick(Marketplace.WALLETS);
        return new LedgerOperation("%s freeze %s".formatted(key, idOf(wallet)), ledger -> ledger.freeze(wallet));
    }

    private LedgerOperation unfreeze(String key) {
        Account wallet = pick(Marketplace.WALLETS);
        return new LedgerOperation("%s unfreeze %s".formatted(key, idOf(wallet)), ledger -> ledger.unfreeze(wallet));
    }

    private LedgerOperation retry(String key) {
        if (generated.isEmpty()) {
            return sale(key);
        }
        LedgerOperation previous = pick(generated);
        return new LedgerOperation("%s retry [%s]".formatted(key, previous.description()), previous.action());
    }

    private LedgerOperation advanceClock(String key) {
        Duration step = Duration.ofMinutes(random.nextInt(MAX_CLOCK_STEP_MINUTES + 1));
        return new LedgerOperation("%s advance clock by %s".formatted(key, step), _ -> clock.advance(step));
    }

    private LedgerOperation post(String label, Transaction transaction) {
        transactionKeys.add(transaction.idempotencyKey());
        return new LedgerOperation(describe(label, transaction), ledger -> ledger.post(transaction));
    }

    private Money amountUpTo(Currency currency, int maxCents) {
        return new Money(BigDecimal.valueOf(1 + random.nextInt(maxCents), 2), currency);
    }

    private <T> T pick(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }

    private static int centsOf(Money money) {
        return money.amount().movePointRight(2).intValueExact();
    }

    private static String describe(String label, Transaction transaction) {
        StringJoiner entries = new StringJoiner(", ");
        for (Entry entry : transaction.entries()) {
            entries.add("%s %s %s".formatted(entry.side(), idOf(entry.account()), textOf(entry.amount())));
        }
        return "%s %s: %s".formatted(transaction.idempotencyKey().value(), label, entries);
    }

    private static String idOf(Account account) {
        return account.id().value();
    }

    private static String textOf(Money money) {
        return money.amount().toPlainString() + " " + money.currency().getCurrencyCode();
    }

    private enum Kind {
        SALE, PAYOUT, FEE, TRANSFER, REFUND, REVERSAL,
        PLACE_HOLD, CAPTURE_HOLD, RELEASE_HOLD,
        FREEZE, UNFREEZE, RETRY, ADVANCE_CLOCK
    }

    private record Sale(String key, Account wallet, Money amount) {
    }
}
