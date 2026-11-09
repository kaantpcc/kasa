package dev.kaantopcu.kasa.core.account;

import java.util.List;
import java.util.Objects;

public record AccountId(String value) {

    private static final String SEGMENT_SEPARATOR = ":";
    private static final int KEEP_EMPTY_SEGMENTS = -1;

    public AccountId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        requireNoBlankSegments(value);
    }

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
