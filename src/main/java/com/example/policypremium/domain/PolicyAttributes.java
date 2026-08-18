package com.example.policypremium.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The inputs a premium is calculated from.
 *
 * <p>Guards its own invariants so the domain cannot hold a nonsensical value regardless of
 * how it was constructed. Request-shaped validation (field messages, HTTP status codes)
 * belongs at the API boundary, not here.
 *
 * @param coverageType kind of cover, never null
 * @param sumInsured amount insured, strictly positive
 * @param region rating region, never null
 * @param priorClaimsCount claims in the prior period, never negative
 */
public record PolicyAttributes(CoverageType coverageType, BigDecimal sumInsured, Region region, int priorClaimsCount) {

    public PolicyAttributes {
        Objects.requireNonNull(coverageType, "coverageType must not be null");
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(sumInsured, "sumInsured must not be null");
        if (sumInsured.signum() <= 0) {
            throw new IllegalArgumentException("sumInsured must be greater than zero");
        }
        if (priorClaimsCount < 0) {
            throw new IllegalArgumentException("priorClaimsCount must not be negative");
        }
    }
}
