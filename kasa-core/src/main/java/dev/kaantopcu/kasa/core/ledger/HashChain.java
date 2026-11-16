package dev.kaantopcu.kasa.core.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

final class HashChain {

    private static final String ALGORITHM = "SHA-256";
    private static final String ABSENT = "";

    private HashChain() {
    }

    static String canonicalOf(Transaction transaction, Instant recordedAt, LocalDate valueDate) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, transaction.idempotencyKey().value());
        append(canonical, recordedAt.toString());
        append(canonical, valueDate.toString());
        append(canonical, transaction.description().orElse(ABSENT));
        append(canonical, transaction.refundOf().map(IdempotencyKey::value).orElse(ABSENT));
        for (Entry entry : transaction.entries()) {
            append(canonical, entry.account().id().value());
            append(canonical, entry.amount().amount().toPlainString());
            append(canonical, entry.amount().currency().getCurrencyCode());
            append(canonical, entry.side().name());
        }
        return canonical.toString();
    }

    static Hash hashOf(Hash previousHash, String canonicalContent) {
        MessageDigest digest = sha256();
        digest.update(previousHash.value().getBytes(StandardCharsets.UTF_8));
        digest.update(canonicalContent.getBytes(StandardCharsets.UTF_8));
        return new Hash(HexFormat.of().formatHex(digest.digest()));
    }

    static Optional<IdempotencyKey> firstBrokenLink(List<PostedTransaction> chain) {
        Hash expectedPrevious = Hash.GENESIS;
        for (PostedTransaction posted : chain) {
            if (!posted.previousHash().equals(expectedPrevious) || !posted.hash().equals(recomputedHashOf(posted))) {
                return Optional.of(posted.transaction().idempotencyKey());
            }
            expectedPrevious = posted.hash();
        }
        return Optional.empty();
    }

    private static Hash recomputedHashOf(PostedTransaction posted) {
        return hashOf(posted.previousHash(), canonicalOf(posted.transaction(), posted.recordedAt(), posted.valueDate()));
    }

    private static void append(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
