package com.example.policypremium.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A calculated premium, retrievable later by {@link #id()}.
 *
 * @param id server-assigned identifier
 * @param attributes the inputs the premium was calculated from
 * @param premium the calculated premium, in {@code RateTable.CURRENCY}
 * @param createdAt when the quote was calculated
 */
public record Quote(UUID id, PolicyAttributes attributes, BigDecimal premium, Instant createdAt) {

    public Quote {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        Objects.requireNonNull(premium, "premium must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
