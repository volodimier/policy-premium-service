package com.example.policypremium.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Every factor that contributed to a premium, so a quoted figure can be audited without
 * re-running the calculation or reading the source.
 *
 * @param baseRate rate for the coverage type before any factors
 * @param regionFactor regional multiplier
 * @param sumInsuredFactor multiplier for the band the sum insured falls in
 * @param claimsLoading multiplier for prior claims, capped
 * @param calculatedPremium product of the above, unrounded and before the floor
 * @param minimumPremiumApplied whether the floor raised the quoted premium
 */
public record PremiumBreakdown(
        BigDecimal baseRate,
        BigDecimal regionFactor,
        BigDecimal sumInsuredFactor,
        BigDecimal claimsLoading,
        BigDecimal calculatedPremium,
        boolean minimumPremiumApplied) {

    public PremiumBreakdown {
        Objects.requireNonNull(baseRate, "baseRate must not be null");
        Objects.requireNonNull(regionFactor, "regionFactor must not be null");
        Objects.requireNonNull(sumInsuredFactor, "sumInsuredFactor must not be null");
        Objects.requireNonNull(claimsLoading, "claimsLoading must not be null");
        Objects.requireNonNull(calculatedPremium, "calculatedPremium must not be null");
    }
}
