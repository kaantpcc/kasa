package dev.kaantopcu.kasa.core.ledger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

final class TestClocks {

    static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-16T09:00:00Z"), ZoneOffset.UTC);

    private TestClocks() {
    }

}
