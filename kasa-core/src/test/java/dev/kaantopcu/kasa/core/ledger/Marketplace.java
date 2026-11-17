package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;

import java.util.Currency;
import java.util.List;

final class Marketplace {

    static final Currency TRY = Currency.getInstance("TRY");
    static final Currency USD = Currency.getInstance("USD");

    static final Account BANK = tryAccount("bank", Side.DEBIT, BalancePolicy.unlimited());
    static final Account FEES = tryAccount("platform:fees", Side.CREDIT, BalancePolicy.nonNegative());
    static final Account FIRST_WALLET = tryAccount("seller:1:wallet", Side.CREDIT, BalancePolicy.nonNegative());
    static final Account FIRST_HELD = tryAccount("seller:1:held", Side.CREDIT, BalancePolicy.nonNegative());
    static final Account SECOND_WALLET = tryAccount("seller:2:wallet", Side.CREDIT,
            BalancePolicy.overdraft(Money.of("100.00", TRY)));
    static final Account SECOND_HELD = tryAccount("seller:2:held", Side.CREDIT, BalancePolicy.nonNegative());
    static final Account USD_BANK = usdAccount("usd-bank", Side.DEBIT, BalancePolicy.unlimited());
    static final Account USD_WALLET = usdAccount("usd-seller:1:wallet", Side.CREDIT, BalancePolicy.nonNegative());

    static final HoldableAccount FIRST_SELLER = new HoldableAccount(FIRST_WALLET, FIRST_HELD);
    static final HoldableAccount SECOND_SELLER = new HoldableAccount(SECOND_WALLET, SECOND_HELD);

    static final AccountId SELLERS = new AccountId("seller");

    static final List<Account> ALL = List.of(
            BANK, FEES, FIRST_WALLET, FIRST_HELD, SECOND_WALLET, SECOND_HELD, USD_BANK, USD_WALLET);
    static final List<Account> SELLER_ACCOUNTS = List.of(FIRST_WALLET, FIRST_HELD, SECOND_WALLET, SECOND_HELD);
    static final List<Account> WALLETS = List.of(FIRST_WALLET, SECOND_WALLET, USD_WALLET);
    static final List<Account> LIRA_WALLETS = List.of(FIRST_WALLET, SECOND_WALLET);
    static final List<HoldableAccount> HOLDABLE_SELLERS = List.of(FIRST_SELLER, SECOND_SELLER);
    static final List<Account> CAPTURE_DESTINATIONS = List.of(FEES, BANK);

    private Marketplace() {
    }

    static void openAll(Ledger ledger) {
        ledger.open(BANK);
        ledger.open(FEES);
        ledger.open(USD_BANK);
        ledger.open(USD_WALLET);
        ledger.openHoldable(FIRST_SELLER);
        ledger.openHoldable(SECOND_SELLER);
    }

    static Account bankFor(Account wallet) {
        if (wallet.currency().equals(USD)) {
            return USD_BANK;
        }
        return BANK;
    }

    private static Account tryAccount(String id, Side normalBalance, BalancePolicy policy) {
        return new Account(new AccountId(id), TRY, normalBalance, policy);
    }

    private static Account usdAccount(String id, Side normalBalance, BalancePolicy policy) {
        return new Account(new AccountId(id), USD, normalBalance, policy);
    }
}
