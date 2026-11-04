package dev.kaantopcu.kasa.core.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
}
