package dev.kaantopcu.kasa.core.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void createsIdempotencyKeyFromValue() {
        IdempotencyKey key = new IdempotencyKey("order-123");

        assertThat(key.value()).isEqualTo("order-123");
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new IdempotencyKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }

    @Test
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> new IdempotencyKey(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key must not be blank");
    }

    @Test
    void rejectsWhiteSpaceOnlyValue() {
        assertThatThrownBy(() -> new IdempotencyKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key must not be blank");
    }

    @Test
    void treatsKeysWithSameValueAsEqual() {
        assertThat(new IdempotencyKey("order-123"))
                .isEqualTo(new IdempotencyKey("order-123"));
    }

    @Test
    void addsSuffixToItsValue() {
        IdempotencyKey key = new IdempotencyKey("order-123");

        assertThat(key.withSuffix(IdempotencyKey.REVERSAL_SUFFIX)).isEqualTo(new IdempotencyKey("order-123:reversal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {":reversal", ":capture", ":release"})
    void rejectsKeyEndingWithReservedSuffix(String suffix) {
        IdempotencyKey key = new IdempotencyKey("order-123" + suffix);

        assertThatThrownBy(key::requireNotReserved)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency key order-123%s ends with %s, which is reserved for transactions the ledger creates"
                        .formatted(suffix, suffix));
    }

    @ParameterizedTest
    @ValueSource(strings = {"order:capture:2", "payout-7:Capture", "payout-7-release", "reversal", "order-123:reversals"})
    void acceptsKeyThatOnlyResemblesReservedSuffix(String value) {
        IdempotencyKey key = new IdempotencyKey(value);

        assertThatCode(key::requireNotReserved).doesNotThrowAnyException();
    }
}
