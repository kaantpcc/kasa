package dev.kaantopcu.kasa.core.ledger;

import dev.kaantopcu.kasa.core.account.Account;
import dev.kaantopcu.kasa.core.account.AccountId;
import dev.kaantopcu.kasa.core.account.BalancePolicy;
import dev.kaantopcu.kasa.core.account.Side;
import dev.kaantopcu.kasa.core.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainTest {

    private static final Currency TRY = Currency.getInstance("TRY");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Account CUSTOMER_PAYMENTS = account("customer-payments", Side.DEBIT);
    private static final Account SELLER_WALLET = account("seller:42:wallet", Side.CREDIT);
    private static final Account OTHER_SELLER_WALLET = account("seller:43:wallet", Side.CREDIT);
    private static final Instant RECORDED_AT = Instant.parse("2026-11-16T09:00:00Z");
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 11, 16);

    @Test
    void hashesPreviousHashFollowedByContentWithSha256() {
        Hash hash = HashChain.hashOf(Hash.GENESIS, "3:abc");

        assertThat(hash.value()).isEqualTo("8c65749a5e52eec4b8ca15d73063548a994136940305047c04bd4eaa303fd68a");
    }

    @Test
    void givesSameHashForSameContent() {
        String content = HashChain.canonicalOf(sale("order-1", "900.00"), RECORDED_AT, VALUE_DATE);

        assertThat(HashChain.hashOf(Hash.GENESIS, content)).isEqualTo(HashChain.hashOf(Hash.GENESIS, content));
    }

    @Test
    void changesHashWhenOneCentChanges() {
        String original = HashChain.canonicalOf(sale("order-1", "900.00"), RECORDED_AT, VALUE_DATE);
        String changed = HashChain.canonicalOf(sale("order-1", "900.01"), RECORDED_AT, VALUE_DATE);

        assertThat(HashChain.hashOf(Hash.GENESIS, changed)).isNotEqualTo(HashChain.hashOf(Hash.GENESIS, original));
    }

    @Test
    void changesHashWhenPreviousHashChanges() {
        String content = HashChain.canonicalOf(sale("order-1", "900.00"), RECORDED_AT, VALUE_DATE);
        Hash otherPrevious = HashChain.hashOf(Hash.GENESIS, "3:abc");

        assertThat(HashChain.hashOf(otherPrevious, content)).isNotEqualTo(HashChain.hashOf(Hash.GENESIS, content));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentWithOneChangedField")
    void changesCanonicalContentWhenAnyHashedFieldChanges(String changedField, String changedContent) {
        String original = canonicalOf(sale("order-1", "900.00"));

        assertThat(changedContent).isNotEqualTo(original);
    }

    static Stream<Arguments> contentWithOneChangedField() {
        Transaction sale = sale("order-1", "900.00");
        return Stream.of(
                Arguments.of("idempotency key", canonicalOf(sale("order-2", "900.00"))),
                Arguments.of("recorded at", HashChain.canonicalOf(sale, RECORDED_AT.plusSeconds(1), VALUE_DATE)),
                Arguments.of("value date", HashChain.canonicalOf(sale, RECORDED_AT, VALUE_DATE.plusDays(1))),
                Arguments.of("description", canonicalOf(saleBuilder().description("Order 1").build())),
                Arguments.of("refund link", canonicalOf(saleBuilder().refundOf("order-0").build())),
                Arguments.of("account", canonicalOf(Transaction.builder("order-1")
                        .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                        .credit(OTHER_SELLER_WALLET, lira("900.00"))
                        .build())),
                Arguments.of("currency", canonicalOf(dollarSale())),
                Arguments.of("side", canonicalOf(Transaction.builder("order-1")
                        .credit(CUSTOMER_PAYMENTS, lira("900.00"))
                        .debit(SELLER_WALLET, lira("900.00"))
                        .build())));
    }

    @Test
    void lengthPrefixesKeepNeighbouringValuesApart() {
        Transaction descriptionAbRefundOfC = linkedRefund("ab", "c");
        Transaction descriptionARefundOfBc = linkedRefund("a", "bc");

        assertThat(HashChain.canonicalOf(descriptionAbRefundOfC, RECORDED_AT, VALUE_DATE))
                .isNotEqualTo(HashChain.canonicalOf(descriptionARefundOfBc, RECORDED_AT, VALUE_DATE));
    }

    @Test
    void findsNoBrokenLinkInIntactChain() {
        List<PostedTransaction> chain = chainOf(sale("order-1", "900.00"), sale("order-2", "100.00"), sale("order-3", "50.00"));

        assertThat(HashChain.firstBrokenLink(chain)).isEmpty();
    }

    @Test
    void findsTransactionWhoseContentWasChanged() {
        List<PostedTransaction> chain = chainOf(sale("order-1", "900.00"), sale("order-2", "100.00"), sale("order-3", "50.00"));
        PostedTransaction original = chain.get(1);
        PostedTransaction tampered = new PostedTransaction(sale("order-2", "9000.00"), original.recordedAt(),
                original.valueDate(), original.previousHash(), original.hash());

        List<PostedTransaction> tamperedChain = List.of(chain.get(0), tampered, chain.get(2));

        assertThat(HashChain.firstBrokenLink(tamperedChain)).contains(new IdempotencyKey("order-2"));
    }

    @Test
    void findsNextTransactionWhenChangedOneAlsoGotANewHash() {
        List<PostedTransaction> chain = chainOf(sale("order-1", "900.00"), sale("order-2", "100.00"), sale("order-3", "50.00"));
        PostedTransaction original = chain.get(1);
        Transaction changedSale = sale("order-2", "9000.00");
        Hash recomputed = HashChain.hashOf(original.previousHash(), HashChain.canonicalOf(changedSale, RECORDED_AT, VALUE_DATE));
        PostedTransaction rehashed = new PostedTransaction(changedSale, RECORDED_AT, VALUE_DATE, original.previousHash(), recomputed);

        List<PostedTransaction> tamperedChain = List.of(chain.get(0), rehashed, chain.get(2));

        assertThat(HashChain.firstBrokenLink(tamperedChain)).contains(new IdempotencyKey("order-3"));
    }

    @Test
    void findsFirstTransactionWhenChainDoesNotStartFromGenesis() {
        Transaction sale = sale("order-1", "900.00");
        Hash notGenesis = HashChain.hashOf(Hash.GENESIS, "3:abc");
        Hash hash = HashChain.hashOf(notGenesis, HashChain.canonicalOf(sale, RECORDED_AT, VALUE_DATE));
        PostedTransaction first = new PostedTransaction(sale, RECORDED_AT, VALUE_DATE, notGenesis, hash);

        assertThat(HashChain.firstBrokenLink(List.of(first))).contains(new IdempotencyKey("order-1"));
    }

    @Test
    void findsNoBrokenLinkInEmptyChain() {
        assertThat(HashChain.firstBrokenLink(List.of())).isEmpty();
    }

    private static List<PostedTransaction> chainOf(Transaction... transactions) {
        Hash previous = Hash.GENESIS;
        List<PostedTransaction> chain = new ArrayList<>();
        for (Transaction transaction : transactions) {
            Hash hash = HashChain.hashOf(previous, HashChain.canonicalOf(transaction, RECORDED_AT, VALUE_DATE));
            chain.add(new PostedTransaction(transaction, RECORDED_AT, VALUE_DATE, previous, hash));
            previous = hash;
        }
        return chain;
    }

    private static Transaction sale(String key, String amount) {
        return Transaction.builder(key)
                .debit(CUSTOMER_PAYMENTS, lira(amount))
                .credit(SELLER_WALLET, lira(amount))
                .build();
    }

    private static Transaction.Builder saleBuilder() {
        return Transaction.builder("order-1")
                .debit(CUSTOMER_PAYMENTS, lira("900.00"))
                .credit(SELLER_WALLET, lira("900.00"));
    }

    private static Transaction dollarSale() {
        Money dollars = Money.of("900.00", USD);
        return Transaction.builder("order-1")
                .debit(new Account(CUSTOMER_PAYMENTS.id(), USD, Side.DEBIT, BalancePolicy.unlimited()), dollars)
                .credit(new Account(SELLER_WALLET.id(), USD, Side.CREDIT, BalancePolicy.unlimited()), dollars)
                .build();
    }

    private static String canonicalOf(Transaction transaction) {
        return HashChain.canonicalOf(transaction, RECORDED_AT, VALUE_DATE);
    }

    private static Transaction linkedRefund(String description, String originalKey) {
        return Transaction.builder("refund-1")
                .refundOf(originalKey)
                .debit(SELLER_WALLET, lira("100.00"))
                .credit(CUSTOMER_PAYMENTS, lira("100.00"))
                .description(description)
                .build();
    }

    private static Money lira(String amount) {
        return Money.of(amount, TRY);
    }

    private static Account account(String id, Side normalBalance) {
        return new Account(new AccountId(id), TRY, normalBalance, BalancePolicy.unlimited());
    }
}
