package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerExceptionTest {

    private static final AccountId SELLER_WALLET = new AccountId("seller:42:wallet");
    private static final Currency TRY = Currency.getInstance("TRY");

    @Test
    void mapsEveryLedgerRejectionToItsOwnErrorCode() {
        LedgerException insufficientFunds = new InsufficientFundsException(
                SELLER_WALLET, Money.of("200.00", TRY), Money.of("1000.00", TRY));
        LedgerException frozen = new AccountFrozenException(SELLER_WALLET);

        assertThat(errorCode(insufficientFunds)).isEqualTo("insufficient-funds");
        assertThat(errorCode(frozen)).isEqualTo("account-frozen");
    }

    private static String errorCode(LedgerException rejection) {
        return switch (rejection) {
            case AccountNotOpenException rejected -> "account-not-open";
            case AccountDefinitionConflictException rejected -> "account-definition-conflict";
            case AccountFrozenException rejected -> "account-frozen";
            case AccountClosedException rejected -> "account-closed";
            case NonZeroBalanceException rejected -> "non-zero-balance";
            case InsufficientFundsException rejected -> "insufficient-funds";
            case IdempotencyKeyConflictException rejected -> "idempotency-key-conflict";
            case TransactionNotFoundException rejected -> "transaction-not-found";
            case RefundExceedsOriginalException rejected -> "refund-exceeds-original";
            case HoldAlreadySettledException rejected -> "hold-already-settled";
            case HeldAccountPostingException rejected -> "held-account-posting";
            case ActiveHoldsException rejected -> "active-holds";
            case HeldAccountNotEmptyException rejected -> "held-account-not-empty";
        };
    }
}
