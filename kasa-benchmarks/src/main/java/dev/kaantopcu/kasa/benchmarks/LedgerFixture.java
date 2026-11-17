package dev.kaantopcu.kasa.benchmarks;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.ledger.IdempotencyKey;
import dev.kaantopcu.kasa.core.ledger.InMemoryLedger;
import dev.kaantopcu.kasa.core.ledger.Ledger;
import dev.kaantopcu.kasa.core.ledger.Transaction;
import dev.kaantopcu.kasa.core.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

final class LedgerFixture {

    private static final int WALLET_COUNT = 100;
    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Account BANK = new Account(new AccountId("bank"), TRY, Side.DEBIT, BalancePolicy.unlimited());
    private static final Money SALE_AMOUNT = Money.of("100.00", TRY);
    private static final Money PAYOUT_AMOUNT = Money.of("0.01", TRY);

    private LedgerFixture() {
    }

    static List<Account> wallets() {
        List<Account> wallets = new ArrayList<>();
        for (int seller = 0; seller < WALLET_COUNT; seller++) {
            AccountId id = new AccountId("seller:" + seller + ":wallet");
            wallets.add(new Account(id, TRY, Side.CREDIT, BalancePolicy.nonNegative()));
        }
        return List.copyOf(wallets);
    }

    static Ledger ledgerWithSales(List<Account> wallets, int saleCount) {
        Ledger ledger = new InMemoryLedger(Clock.systemUTC());
        ledger.open(BANK);
        wallets.forEach(ledger::open);
        for (int sale = 0; sale < saleCount; sale++) {
            ledger.post(sale("sale-" + sale, wallets.get(sale % wallets.size())));
        }
        return ledger;
    }

    static Transaction sale(String key, Account wallet) {
        return Transaction.builder(key)
                .debit(BANK, SALE_AMOUNT)
                .credit(wallet, SALE_AMOUNT)
                .build();
    }

    static Transaction payout(String key, Account wallet) {
        return Transaction.builder(key)
                .debit(wallet, PAYOUT_AMOUNT)
                .credit(BANK, PAYOUT_AMOUNT)
                .build();
    }

    static Instant recordedAtOfSale(Ledger ledger, int sale) {
        return ledger.posted(new IdempotencyKey("sale-" + sale)).orElseThrow().recordedAt();
    }
}
