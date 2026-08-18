package com.example.policypremium.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A calculated premium, retrievable later by {@link #id()}.
 *
 * <p>Holds the whole {@link PremiumCalculation} rather than flattening premium, currency and
 * breakdown into separate fields: those three are one coherent result, and splitting them
 * would create state that can disagree with itself.
 *
 * <p>The calculation is <strong>stored, not recomputed on read</strong>. Recomputing would
 * avoid duplication but would silently reprice an issued quote the moment rates changed. A
 * quote is a promise made at a point in time, so it keeps the numbers it was issued with.
 *
 * @param id server-assigned identifier
 * @param attributes the inputs the premium was calculated from
 * @param calculation the premium and the factors that produced it
 * @param createdAt when the quote was calculated
 */
public record Quote(UUID id, PolicyAttributes attributes, PremiumCalculation calculation, Instant createdAt) {

    public Quote {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        Objects.requireNonNull(calculation, "calculation must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
