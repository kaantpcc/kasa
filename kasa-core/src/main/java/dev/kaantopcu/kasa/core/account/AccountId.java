package dev.kaantopcu.kasa.core.account;

import java.util.List;
import java.util.Objects;

/**
 * The unique id of an account.
 *
 * <p>Ids may be split into segments with {@code ':'} to build a hierarchy, for example
 * {@code "seller:42:wallet"} and {@code "seller:42:held"} both sit under {@code "seller:42"}.
 * The ledger can then sum a whole branch with
 * {@link dev.kaantopcu.kasa.core.ledger.LedgerReader#balanceUnder(AccountId)}.
 *
 * @param value the id text; not blank and without blank segments
 */
public record AccountId(String value) {

    private static final String SEGMENT_SEPARATOR = ":";
    private static final int KEEP_EMPTY_SEGMENTS = -1;

    /**
     * Creates an account id.
     *
     * @param value the id text, such as {@code "seller:42:wallet"}
     * @throws IllegalArgumentException if {@code value} is blank or has a blank segment, such as {@code "seller::wallet"}
     */
    public AccountId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        requireNoBlankSegments(value);
    }

    /**
     * Tells whether this id is the given branch or sits under it.
     *
     * <p>Segments are compared whole: {@code "seller:42:wallet"} is within {@code "seller:42"} and
     * {@code "seller"}, but not within {@code "seller:4"}.
     *
     * @param branch the branch to check against
     * @return {@code true} if this id equals {@code branch} or starts with all of its segments
     */
    public boolean isWithin(AccountId branch) {
        Objects.requireNonNull(branch, "branch");
        List<String> ownSegments = segmentsOf(value);
        List<String> branchSegments = segmentsOf(branch.value());
        return ownSegments.size() >= branchSegments.size()
                && ownSegments.subList(0, branchSegments.size()).equals(branchSegments);
    }

    private static void requireNoBlankSegments(String value) {
        for (String segment : segmentsOf(value)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Account id must not contain blank segments, got %s".formatted(value));
            }
        }
    }

    private static List<String> segmentsOf(String value) {
        return List.of(value.split(SEGMENT_SEPARATOR, KEEP_EMPTY_SEGMENTS));
    }
}
