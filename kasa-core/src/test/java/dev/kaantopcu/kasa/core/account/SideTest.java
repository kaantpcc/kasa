package dev.kaantopcu.kasa.core.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SideTest {

    @Test
    void oppositeOfDebitIsCredit() {
        assertThat(Side.DEBIT.opposite()).isEqualTo(Side.CREDIT);
    }

    @Test
    void oppositeOfCreditIsDebit() {
        assertThat(Side.CREDIT.opposite()).isEqualTo(Side.DEBIT);
    }
}
