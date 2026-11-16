package dev.kaantopcu.kasa.core.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashTest {

    private static final String VALID = "8c65749a5e52eec4b8ca15d73063548a994136940305047c04bd4eaa303fd68a";

    @Test
    void acceptsSixtyFourLowercaseHexCharacters() {
        assertThat(new Hash(VALID).value()).isEqualTo(VALID);
    }

    @Test
    void genesisIsSixtyFourZeros() {
        assertThat(Hash.GENESIS.value()).isEqualTo("0".repeat(64));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "abc",
            "8C65749A5E52EEC4B8CA15D73063548A994136940305047C04BD4EAA303FD68A",
            "8c65749a5e52eec4b8ca15d73063548a994136940305047c04bd4eaa303fd68g",
            "8c65749a5e52eec4b8ca15d73063548a994136940305047c04bd4eaa303fd68a0"
    })
    void rejectsValueThatIsNotSixtyFourLowercaseHexCharacters(String value) {
        assertThatThrownBy(() -> new Hash(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hash must be 64 lowercase hex characters, got %s".formatted(value));
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new Hash(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }
}
