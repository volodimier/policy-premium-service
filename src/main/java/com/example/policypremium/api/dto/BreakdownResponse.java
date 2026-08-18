package com.example.policypremium.api.dto;

import com.example.policypremium.rules.PremiumBreakdown;
import java.math.BigDecimal;

/**
 * How a premium was arrived at, exposed so a quote can be audited from the response alone.
 *
 * <p>A separate type from {@link PremiumBreakdown} on purpose: serialising the rules type
 * directly would make the published API contract change silently whenever an internal type
 * was refactored. The mapping below is the seam where that would be caught.
 *
 * @param baseRate rate for the coverage type before any factors
 * @param regionFactor regional multiplier
 * @param sumInsuredFactor multiplier for the band the sum insured falls in
 * @param claimsLoading multiplier for prior claims, capped
 * @param calculatedPremium product of the above, before the minimum-premium floor
 * @param minimumPremiumApplied whether the floor raised the quoted premium
 */
public record BreakdownResponse(
        BigDecimal baseRate,
        BigDecimal regionFactor,
        BigDecimal sumInsuredFactor,
        BigDecimal claimsLoading,
        BigDecimal calculatedPremium,
        boolean minimumPremiumApplied) {

    /** Maps the internal breakdown onto the published response shape. */
    public static BreakdownResponse from(PremiumBreakdown breakdown) {
        return new BreakdownResponse(
                breakdown.baseRate(),
                breakdown.regionFactor(),
                breakdown.sumInsuredFactor(),
                breakdown.claimsLoading(),
                breakdown.calculatedPremium(),
                breakdown.minimumPremiumApplied());
    }
}
