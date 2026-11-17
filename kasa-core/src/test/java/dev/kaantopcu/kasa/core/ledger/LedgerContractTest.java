package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.AccountStatus;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.HoldableAccount;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static dev.kaantopcu.kasa.core.account.BalancePolicy.nonNegative;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.overdraft;
import static dev.kaantopcu.kasa.core.account.BalancePolicy.unlimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class LedgerContractTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT, unlimited());
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT, nonNegative());
    private static final Account COMMISSION = account("commission", Side.CREDIT, unlimited());
    private static final Account BANK = account("bank", Side.DEBIT, unlimited());
    private static final HoldableAccount SELLER = new HoldableAccount(
            SELLER_WALLET,
            account("seller:42:wallet:held", Side.CREDIT, nonNegative()));
    private static final Account CREDIT_CUSTOMER = account("customer:7:wallet", Side.CREDIT, overdraft(lira("500.00")));
    private static final HoldableAccount CREDIT_CUSTOMER_CARD = new HoldableAccount(
            CREDIT_CUSTOMER,
            account("customer:7:wallet:held", Side.CREDIT, nonNegative()));
    private static final IdempotencyKey HOLD_KEY = new IdempotencyKey("payout-1-hold");

    private Ledger ledger;

    protected abstract Ledger createLedger();

    @BeforeEach
    void createLedgerWithOpenAccounts() {
        ledger = createLedger();
        ledger.open(CUSTOMER_PAYMENTS);
        ledger.open(SELLER_WALLET);
        ledger.open(COMMISSION);
        ledger.open(BANK);
        ledger.open(SELLER.held());
        ledger.open(CREDIT_CUSTOMER);
        ledger.open(CREDIT_CUSTOMER_CARD.held());
    }

    @Test
    void returnsZeroBalanceWhenLedgerIsEmpty() {
        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void readsEachAccountBalanceOnItsNormalSide() {
        ledger.post(sale("order-1"));

        assertThat(ledger.balance(CUSTOMER_PAYMENTS)).isEqualTo(lira("1000.00"));
        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
        assertThat(ledger.balance(COMMISSION)).isEqualTo(lira("100.00"));
    }

    @Test
    void subtractsEntriesOnOppositeSideOfNormalBalance() {
        ledger.post(sale("order-1"));

        ledger.post(Transaction.builder("payout-1")
                .debit(SELLER_WALLET, lira("300.00"))
                .credit(BANK, lira("300.00"))
                .build());

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    @Test
    void addsUpEntriesFromSeveralTransactions() {
        ledger.post(sale("order-1"));
        ledger.post(sale("order-2"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("1800.00"));
    }

    @Test
    void ignoresTransactionPostedTwiceWithSameKey() {
        ledger.post(sale("order-1"));

        ledger.post(sale("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void keepsSingleCopyOfEntriesWhenTransactionIsPostedTwice() {
        ledger.post(sale("order-1"));

        ledger.post(sale("order-1"));

        assertThat(ledger.entries(SELLER_WALLET)).hasSize(1);
    }

    @Test
    void rejectsSameKeyUsedForDifferentTransaction() {
        ledger.post(sale("order-1"));
        Transaction differentOrder = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("500.00"))
                .credit(SELLER_WALLET, lira("500.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(differentOrder))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Idempotency key order-1 was already used for a different transaction");
    }

    @Test
    void keepsOriginalTransactionWhenKeyConflicts() {
        ledger.post(sale("order-1"));
        Transaction differentOrder = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("500.00"))
                .credit(SELLER_WALLET, lira("500.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(differentOrder))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void rejectsSameKeyUsedWithDifferentDescription() {
        ledger.post(sale("order-1"));
        Transaction sameSaleWithDescription = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("1000.00"))
                .credit(SELLER_WALLET, lira("900.00"))
                .credit(COMMISSION, lira("100.00"))
                .description("Order 1 payment")
                .build();

        assertThatThrownBy(() -> ledger.post(sameSaleWithDescription))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void rejectsSameKeyUsedWithDifferentRefundLink() {
        ledger.post(sale("order-1"));
        ledger.post(refund("refund-1", "order-1"));

        Transaction sameRefundForAnotherOrder = refund("refund-1", "order-2");

        assertThatThrownBy(() -> ledger.post(sameRefundForAnotherOrder))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void acceptsPartialRefund() {
        ledger.post(sale("order-1"));

        ledger.post(refund("refund-1", "order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
        assertThat(ledger.balance(CUSTOMER_PAYMENTS)).isEqualTo(lira("700.00"));
    }

    @Test
    void acceptsSeveralRefundsUpToOriginalAmount() {
        ledger.post(sale("order-1"));

        ledger.post(refund("refund-1", "order-1"));
        ledger.post(refund("refund-2", "order-1"));
        ledger.post(refund("refund-3", "order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsRefundsExceedingOriginalAmount() {
        ledger.post(sale("order-1"));
        ledger.post(refund("refund-1", "order-1"));
        ledger.post(refund("refund-2", "order-1"));
        ledger.post(refund("refund-3", "order-1"));
        Transaction oneRefundTooMany = refund("refund-4", "order-1");

        assertThatThrownBy(() -> ledger.post(oneRefundTooMany))
                .isInstanceOf(RefundExceedsOriginalException.class)
                .hasMessage("Refund exceeds what can be refunded on seller:42:wallet: refundable 0.00 TRY, requested 300.00 TRY");
    }

    @Test
    void writesNothingWhenRefundIsRejected() {
        ledger.post(sale("order-1"));
        ledger.post(refund("refund-1", "order-1"));
        ledger.post(refund("refund-2", "order-1"));
        ledger.post(refund("refund-3", "order-1"));
        Transaction oneRefundTooMany = refund("refund-4", "order-1");

        assertThatThrownBy(() -> ledger.post(oneRefundTooMany))
                .isInstanceOf(RefundExceedsOriginalException.class);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
        assertThat(ledger.posted(new IdempotencyKey("refund-4"))).isEmpty();
    }

    @Test
    void ignoresRetryOfAcceptedRefund() {
        ledger.post(sale("order-1"));
        ledger.post(refund("refund-1", "order-1"));

        ledger.post(refund("refund-1", "order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("600.00"));
    }

    @Test
    void rejectsRefundOfUnknownTransaction() {
        Transaction refundOfUnknownOrder = refund("refund-1", "order-404");

        assertThatThrownBy(() -> ledger.post(refundOfUnknownOrder))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("No transaction posted with idempotency key order-404");
    }

    @Test
    void rejectsRefundToAccountThatWasNotInOriginal() {
        ledger.post(sale("order-1"));
        Transaction refundToBank = Transaction.builder("refund-1")
                .refundOf("order-1")
                .debit(SELLER_WALLET, lira("100.00"))
                .credit(BANK, lira("100.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(refundToBank))
                .isInstanceOf(RefundExceedsOriginalException.class)
                .hasMessage("Refund exceeds what can be refunded on bank: refundable 0.00 TRY, requested 100.00 TRY");
    }

    @Test
    void rejectsRefundInSameDirectionAsOriginal() {
        ledger.post(sale("order-1"));
        Transaction paysSellerAgain = Transaction.builder("refund-1")
                .refundOf("order-1")
                .credit(SELLER_WALLET, lira("100.00"))
                .debit(CUSTOMER_PAYMENTS, lira("100.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(paysSellerAgain))
                .isInstanceOf(RefundExceedsOriginalException.class)
                .hasMessage("Refund exceeds what can be refunded on seller:42:wallet: refundable 900.00 TRY, requested -100.00 TRY");
    }

    @Test
    void rejectsFullReversalAfterPartialRefund() {
        ledger.post(sale("order-1"));
        ledger.post(refund("refund-1", "order-1"));
        IdempotencyKey orderKey = new IdempotencyKey("order-1");

        assertThatThrownBy(() -> ledger.reverse(orderKey))
                .isInstanceOf(RefundExceedsOriginalException.class)
                .hasMessage("Refund exceeds what can be refunded on customer-payments: refundable 700.00 TRY, requested 1000.00 TRY");
    }

    @Test
    void rejectsRefundAfterFullReversal() {
        ledger.post(sale("order-1"));
        ledger.reverse(new IdempotencyKey("order-1"));
        Transaction lateRefund = refund("refund-1", "order-1");

        assertThatThrownBy(() -> ledger.post(lateRefund))
                .isInstanceOf(RefundExceedsOriginalException.class)
                .hasMessage("Refund exceeds what can be refunded on seller:42:wallet: refundable 0.00 TRY, requested 300.00 TRY");
    }

    @Test
    void ignoresEntriesOfAnotherAccounts() {
        ledger.post(sale("order-1"));

        assertThat(ledger.balance(BANK)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsNullTransaction() {
        assertThatThrownBy(() -> ledger.post(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("transaction");
    }

    @Test
    void rejectsNullAccount() {
        assertThatThrownBy(() -> ledger.balance(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void returnsNoEntriesWhenAccountHasNoEntries() {
        ledger.post(sale("order-1"));

        assertThat(ledger.entries(BANK)).isEmpty();
    }

    @Test
    void returnsOnlyEntriesOfGivenAccountInPostingOrder() {
        ledger.post(sale("order-1"));
        ledger.post(Transaction.builder("payout-1")
                .debit(SELLER_WALLET, lira("300.00"))
                .credit(BANK, lira("300.00"))
                .build());

        assertThat(ledger.entries(SELLER_WALLET)).containsExactly(
                new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT),
                new Entry(SELLER_WALLET, lira("300.00"), Side.DEBIT));
    }

    @Test
    void doesNotAllowEntriesToBeModified() {
        ledger.post(sale("order-1"));

        assertThatThrownBy(() -> ledger.entries(SELLER_WALLET).add(
                new Entry(SELLER_WALLET, lira("50.00"), Side.CREDIT)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullAccountForEntries() {
        assertThatThrownBy(() -> ledger.entries(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void reversalCancelsOriginalTransaction() {
        ledger.post(sale("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(COMMISSION)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void keepsOriginalAndReversalEntries() {
        ledger.post(sale("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.entries(SELLER_WALLET)).containsExactly(
                new Entry(SELLER_WALLET, lira("900.00"), Side.CREDIT),
                new Entry(SELLER_WALLET, lira("900.00"), Side.DEBIT));
    }

    @Test
    void ignoresSecondReversalOfSameTransaction() {
        ledger.post(sale("order-1"));
        ledger.reverse(new IdempotencyKey("order-1"));

        ledger.reverse(new IdempotencyKey("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsReversalOfUnknownTransaction() {
        assertThatThrownBy(() -> ledger.reverse(new IdempotencyKey("order-999")))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("No transaction posted with idempotency key order-999");
    }

    @Test
    void rejectsNullKeyForReversal() {
        assertThatThrownBy(() -> ledger.reverse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    @Test
    void totalBalanceIncludesHeldAmount() {
        fundSeller("600.00");

        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        assertThat(ledger.totalBalance(SELLER)).isEqualTo(lira("600.00"));
    }

    @Test
    void totalBalanceDropsAfterCapture() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        ledger.captureHold(HOLD_KEY, BANK, lira("300.00"));

        assertThat(ledger.totalBalance(SELLER)).isEqualTo(lira("300.00"));
    }

    @Test
    void rejectsNullAccountForTotalBalance() {
        assertThatThrownBy(() -> ledger.totalBalance(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void placeHoldMovesAmountWhenAvailableBalanceIsEnough() {
        fundSeller("600.00");

        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
    }

    @Test
    void placeHoldAllowsHoldingEntireAvailableBalance() {
        fundSeller("600.00");

        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("600.00")));

        assertThat(ledger.balance(SELLER.available())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsHoldWhenAvailableBalanceIsInsufficient() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        Hold secondHold = new Hold(new IdempotencyKey("payout-2-hold"), SELLER, lira("600.00"));

        assertThatThrownBy(() -> ledger.placeHold(secondHold))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient available balance in seller:42:wallet: available 300.00 TRY, requested 600.00 TRY");
    }

    @Test
    void keepsBalancesUnchangedWhenHoldIsRejected() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        Hold secondHold = new Hold(new IdempotencyKey("payout-2-hold"), SELLER, lira("600.00"));

        assertThatThrownBy(() -> ledger.placeHold(secondHold))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
    }

    @Test
    void rejectsNullHold() {
        assertThatThrownBy(() -> ledger.placeHold(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("hold");
    }

    @Test
    void ignoresHoldPlacedTwiceWithSameKey() {
        fundSeller("300.00");
        Hold hold = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        ledger.placeHold(hold);

        ledger.placeHold(hold);

        assertThat(ledger.balance(SELLER.available())).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
    }

    @Test
    void rejectsHoldWithReusedKeyAndDifferentAmount() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        Hold differentHold = new Hold(HOLD_KEY, SELLER, lira("200.00"));

        assertThatThrownBy(() -> ledger.placeHold(differentHold))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Idempotency key payout-1-hold was already used for a different transaction");
    }

    @Test
    void rejectsTransactionToAccountThatIsNotOpen() {
        Transaction toMisspelledWallet = saleToMisspelledWallet();

        assertThatThrownBy(() -> ledger.post(toMisspelledWallet))
                .isInstanceOf(AccountNotOpenException.class)
                .hasMessage("Account seler:42:wallet has not been opened");
    }

    @Test
    void writesNothingWhenTransactionTouchesAccountThatIsNotOpen() {
        Transaction toMisspelledWallet = saleToMisspelledWallet();

        assertThatThrownBy(() -> ledger.post(toMisspelledWallet))
                .isInstanceOf(AccountNotOpenException.class);

        assertThat(ledger.balance(CUSTOMER_PAYMENTS)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void acceptsKeyOfRejectedTransactionForNextAttempt() {
        Transaction toMisspelledWallet = saleToMisspelledWallet();
        assertThatThrownBy(() -> ledger.post(toMisspelledWallet))
                .isInstanceOf(AccountNotOpenException.class);

        ledger.post(sale("order-1"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void rejectsOpeningSameIdWithDifferentDefinition() {
        Account sellerWalletInUsd = new Account(new AccountId("seller:42:wallet"), USD, Side.CREDIT, nonNegative());

        assertThatThrownBy(() -> ledger.open(sellerWalletInUsd))
                .isInstanceOf(AccountDefinitionConflictException.class)
                .hasMessage("Account seller:42:wallet is already open with a different definition");
    }

    @Test
    void rejectsOpeningSameIdWithDifferentBalancePolicy() {
        Account sellerWalletWithOverdraft = account("seller:42:wallet", Side.CREDIT, overdraft(lira("500.00")));

        assertThatThrownBy(() -> ledger.open(sellerWalletWithOverdraft))
                .isInstanceOf(AccountDefinitionConflictException.class)
                .hasMessage("Account seller:42:wallet is already open with a different definition");
    }

    @Test
    void ignoresOpeningSameAccountTwice() {
        ledger.post(sale("order-1"));

        ledger.open(SELLER_WALLET);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("900.00"));
    }

    @Test
    void rejectsTransactionUsingDifferentDefinitionOfOpenAccount() {
        Transaction withWrongDefinition = Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                .credit(account("seller:42:wallet", Side.DEBIT, nonNegative()), lira("900.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(withWrongDefinition))
                .isInstanceOf(AccountDefinitionConflictException.class)
                .hasMessage("Account seller:42:wallet is already open with a different definition");
    }

    @Test
    void rejectsBalanceOfAccountThatIsNotOpen() {
        assertThatThrownBy(() -> ledger.balance(account("seler:42:wallet", Side.CREDIT, nonNegative())))
                .isInstanceOf(AccountNotOpenException.class)
                .hasMessage("Account seler:42:wallet has not been opened");
    }

    @Test
    void rejectsNullAccountWhenOpening() {
        assertThatThrownBy(() -> ledger.open(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("account");
    }

    @Test
    void allowsPayoutOfEntireNonNegativeBalance() {
        fundSeller("200.00");

        ledger.post(payout("payout-1", SELLER_WALLET, "200.00"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsPayoutLargerThanNonNegativeBalance() {
        fundSeller("200.00");
        Transaction tooLargePayout = payout("payout-1", SELLER_WALLET, "1000.00");

        assertThatThrownBy(() -> ledger.post(tooLargePayout))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient available balance in seller:42:wallet: available 200.00 TRY, requested 1000.00 TRY");
    }

    @Test
    void reportsRejectedTransactionAsLedgerException() {
        fundSeller("200.00");
        Transaction tooLargePayout = payout("payout-1", SELLER_WALLET, "1000.00");

        assertThatThrownBy(() -> ledger.post(tooLargePayout))
                .isInstanceOf(LedgerException.class);
    }

    @Test
    void writesNothingWhenTransactionBreaksBalancePolicy() {
        fundSeller("200.00");
        Transaction tooLargePayout = payout("payout-1", SELLER_WALLET, "1000.00");

        assertThatThrownBy(() -> ledger.post(tooLargePayout))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(lira("200.00"));
        assertThat(ledger.balance(BANK)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void ignoresRetryOfAcceptedPayoutAfterBalanceReachedZero() {
        fundSeller("200.00");
        ledger.post(payout("payout-1", SELLER_WALLET, "200.00"));

        ledger.post(payout("payout-1", SELLER_WALLET, "200.00"));

        assertThat(ledger.balance(SELLER_WALLET)).isEqualTo(Money.zero(TRY));
    }

    @Test
    void allowsUnlimitedAccountToGoNegative() {
        fundSeller("300.00");

        ledger.post(payout("payout-1", SELLER_WALLET, "300.00"));

        assertThat(ledger.balance(BANK)).isEqualTo(lira("-300.00"));
    }

    @Test
    void allowsOverdraftAccountToGoNegativeUpToItsLimit() {
        ledger.post(payout("purchase-1", CREDIT_CUSTOMER, "500.00"));

        assertThat(ledger.balance(CREDIT_CUSTOMER)).isEqualTo(lira("-500.00"));
    }

    @Test
    void rejectsTransactionBeyondOverdraftLimit() {
        depositToCreditCustomer("100.00");
        Transaction beyondLimit = payout("purchase-1", CREDIT_CUSTOMER, "600.01");

        assertThatThrownBy(() -> ledger.post(beyondLimit))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient available balance in customer:7:wallet: available 600.00 TRY, requested 600.01 TRY");
    }

    @Test
    void placeHoldUsesOverdraftLimit() {
        ledger.placeHold(new Hold(new IdempotencyKey("card-auth-1"), CREDIT_CUSTOMER_CARD, lira("500.00")));

        assertThat(ledger.balance(CREDIT_CUSTOMER)).isEqualTo(lira("-500.00"));
        assertThat(ledger.balance(CREDIT_CUSTOMER_CARD.held())).isEqualTo(lira("500.00"));
    }

    @Test
    void rejectsHoldBeyondOverdraftLimit() {
        Hold beyondLimit = new Hold(new IdempotencyKey("card-auth-1"), CREDIT_CUSTOMER_CARD, lira("500.01"));

        assertThatThrownBy(() -> ledger.placeHold(beyondLimit))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient available balance in customer:7:wallet: available 500.00 TRY, requested 500.01 TRY");
    }

    @Test
    void rejectsReleaseAfterCapture() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.captureHold(HOLD_KEY, BANK, lira("300.00"));

        assertThatThrownBy(() -> ledger.releaseHold(HOLD_KEY))
                .isInstanceOf(HoldAlreadySettledException.class)
                .hasMessage("Hold payout-1-hold is already settled");
    }

    @Test
    void rejectsCaptureAfterRelease() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.releaseHold(HOLD_KEY);
        Money fullAmount = lira("300.00");

        assertThatThrownBy(() -> ledger.captureHold(HOLD_KEY, BANK, fullAmount))
                .isInstanceOf(HoldAlreadySettledException.class)
                .hasMessage("Hold payout-1-hold is already settled");
    }

    @Test
    void rejectsSettlingHoldTwiceWhileAnotherHoldKeepsHeldBalance() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.placeHold(new Hold(new IdempotencyKey("payout-2-hold"), SELLER, lira("300.00")));
        ledger.captureHold(HOLD_KEY, BANK, lira("300.00"));

        assertThatThrownBy(() -> ledger.releaseHold(HOLD_KEY))
                .isInstanceOf(HoldAlreadySettledException.class);

        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
        assertThat(ledger.balance(SELLER.available())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsReversingHoldPlacement() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        assertThatThrownBy(() -> ledger.reverse(HOLD_KEY))
                .isInstanceOf(HeldAccountPostingException.class)
                .hasMessage("Held account seller:42:wallet:held changes only through placeHold, captureHold and releaseHold");

        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
        assertThat(ledger.activeHolds(SELLER)).hasSize(1);
    }

    @Test
    void rejectsReversingHoldCapture() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.captureHold(HOLD_KEY, BANK, lira("200.00"));
        IdempotencyKey captureKey = new IdempotencyKey("payout-1-hold:capture");

        assertThatThrownBy(() -> ledger.reverse(captureKey))
                .isInstanceOf(HeldAccountPostingException.class);

        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
    }

    @Test
    void rejectsPostingToHeldAccount() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        Transaction drainHeld = Transaction.builder("drain-1")
                .debit(SELLER.held(), lira("300.00"))
                .credit(SELLER.available(), lira("300.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(drainHeld))
                .isInstanceOf(HeldAccountPostingException.class);

        assertThat(ledger.balance(SELLER.held())).isEqualTo(lira("300.00"));
    }

    @Test
    void rejectsCapturingIntoHeldAccount() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        Money fullAmount = lira("300.00");

        assertThatThrownBy(() -> ledger.captureHold(HOLD_KEY, SELLER.held(), fullAmount))
                .isInstanceOf(HeldAccountPostingException.class);

        assertThat(ledger.activeHolds(SELLER)).hasSize(1);
    }

    @Test
    void rejectsPostingToHeldAccountOpenedWithOpenHoldable() {
        HoldableAccount newSeller = new HoldableAccount(
                account("seller:43:wallet", Side.CREDIT, nonNegative()),
                account("seller:43:wallet:held", Side.CREDIT, nonNegative()));
        ledger.openHoldable(newSeller);
        Transaction saleIntoHeld = Transaction.builder("order-43")
                .debit(CUSTOMER_PAYMENTS, lira("100.00"))
                .credit(newSeller.held(), lira("100.00"))
                .build();

        assertThatThrownBy(() -> ledger.post(saleIntoHeld))
                .isInstanceOf(HeldAccountPostingException.class);
    }

    @Test
    void captureHoldMovesCapturedAmountAndReturnsRemainder() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        ledger.captureHold(HOLD_KEY, BANK, lira("200.00"));

        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("400.00"));
        assertThat(ledger.balance(BANK)).isEqualTo(lira("-200.00"));
    }

    @Test
    void releaseHoldReturnsHeldAmountToAvailableAccount() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));

        ledger.releaseHold(HOLD_KEY);

        assertThat(ledger.balance(SELLER.held())).isEqualTo(Money.zero(TRY));
        assertThat(ledger.balance(SELLER.available())).isEqualTo(lira("600.00"));
    }

    @Test
    void ignoresRetryOfSameCapture() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.captureHold(HOLD_KEY, BANK, lira("200.00"));

        ledger.captureHold(HOLD_KEY, BANK, lira("200.00"));

        assertThat(ledger.balance(BANK)).isEqualTo(lira("-200.00"));
    }

    @Test
    void rejectsSecondCaptureWithDifferentAmount() {
        fundSeller("600.00");
        ledger.placeHold(new Hold(HOLD_KEY, SELLER, lira("300.00")));
        ledger.captureHold(HOLD_KEY, BANK, lira("200.00"));
        Money differentAmount = lira("100.00");

        assertThatThrownBy(() -> ledger.captureHold(HOLD_KEY, BANK, differentAmount))
                .isInstanceOf(HoldAlreadySettledException.class);
    }

    @Test
    void rejectsSettlingUnknownHold() {
        IdempotencyKey unknownHold = new IdempotencyKey("payout-9-hold");

        assertThatThrownBy(() -> ledger.releaseHold(unknownHold))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("No transaction posted with idempotency key payout-9-hold");
    }

    @Test
    void doesNotRememberHoldThatWasRejected() {
        Hold holdWithoutFunds = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        assertThatThrownBy(() -> ledger.placeHold(holdWithoutFunds))
                .isInstanceOf(InsufficientFundsException.class);

        assertThatThrownBy(() -> ledger.releaseHold(HOLD_KEY))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void activeHoldsListsUnsettledHoldsInPlacementOrder() {
        fundSeller("600.00");
        Hold first = new Hold(HOLD_KEY, SELLER, lira("300.00"));
        Hold second = new Hold(new IdempotencyKey("payout-2-hold"), SELLER, lira("200.00"));
        Hold third = new Hold(new IdempotencyKey("payout-3-hold"), SELLER, lira("100.00"));
        ledger.placeHold(first);
        ledger.placeHold(second);
        ledger.placeHold(third);

        ledger.captureHold(HOLD_KEY, BANK, lira("300.00"));

        assertThat(ledger.activeHolds(SELLER)).containsExactly(second, third);
    }

    @Test
    void activeHoldsIsEmptyWithoutHolds() {
        assertThat(ledger.activeHolds(SELLER)).isEmpty();
    }

    @Test
    void openHoldableOpensBothAccounts() {
        HoldableAccount newSeller = new HoldableAccount(
                account("seller:43:wallet", Side.CREDIT, nonNegative()),
                account("seller:43:wallet:held", Side.CREDIT, nonNegative()));

        ledger.openHoldable(newSeller);

        assertThat(ledger.status(newSeller.available())).isEqualTo(AccountStatus.OPEN);
        assertThat(ledger.status(newSeller.held())).isEqualTo(AccountStatus.OPEN);
    }

    @Test
    void openHoldableOpensNothingWhenOneAccountConflicts() {
        Account newWallet = account("seller:43:wallet", Side.CREDIT, nonNegative());
        HoldableAccount conflicting = new HoldableAccount(newWallet, account("commission", Side.CREDIT, nonNegative()));

        assertThatThrownBy(() -> ledger.openHoldable(conflicting))
                .isInstanceOf(AccountDefinitionConflictException.class);

        assertThatThrownBy(() -> ledger.status(newWallet))
                .isInstanceOf(AccountNotOpenException.class);
    }

    @Test
    void rejectsNullHoldKeyForCapture() {
        Money amount = lira("100.00");

        assertThatThrownBy(() -> ledger.captureHold(null, BANK, amount))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("holdKey");
    }

    @Test
    void rejectsReversalThatWouldMakeBalanceNegative() {
        ledger.post(sale("order-1"));
        ledger.post(payout("payout-1", SELLER_WALLET, "900.00"));

        assertThatThrownBy(() -> ledger.reverse(new IdempotencyKey("order-1")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient available balance in seller:42:wallet: available 0.00 TRY, requested 900.00 TRY");
    }

    private void fundSeller(String amount) {
        ledger.post(Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(SELLER.available(), lira(amount))
                .build());
    }

    private void depositToCreditCustomer(String amount) {
        ledger.post(Transaction.builder("deposit-1")
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(CREDIT_CUSTOMER, lira(amount))
                .build());
    }

    private static Transaction payout(String key, Account from, String amount) {
        return Transaction.builder(key)
                .debit(from, lira(amount))
                .credit(BANK, lira(amount))
                .build();
    }

    private static Transaction sale(String orderKey) {
        return Transaction.builder(orderKey)
                .debit(CUSTOMER_PAYMENTS, lira("1000.00"))
                .credit(SELLER_WALLET, lira("900.00"))
                .credit(COMMISSION, lira("100.00"))
                .build();
    }

    private static Transaction refund(String refundKey, String orderKey) {
        return Transaction.builder(refundKey)
                .refundOf(orderKey)
                .debit(SELLER_WALLET, lira("300.00"))
                .credit(CUSTOMER_PAYMENTS, lira("300.00"))
                .build();
    }

    private static Transaction saleToMisspelledWallet() {
        return Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                .credit(account("seler:42:wallet", Side.CREDIT, nonNegative()), lira("900.00"))
                .build();
    }

    private static Account account(String id, Side normalBalance, BalancePolicy balancePolicy) {
        return new Account(new AccountId(id), TRY, normalBalance, balancePolicy);
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }
}
