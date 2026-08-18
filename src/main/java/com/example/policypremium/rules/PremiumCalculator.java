package com.example.policypremium.rules;

import com.example.policypremium.domain.PolicyAttributes;
import com.example.policypremium.domain.PremiumBreakdown;
import com.example.policypremium.domain.PremiumCalculation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Prices a policy from its attributes.
 *
 * <p>A pure function of its inputs: no state, no Spring, no clock, no identity. The same
 * attributes always produce the same premium, which is what makes the rules cheap to test
 * exhaustively and safe to reason about.
 *
 * <pre>
 * premium = baseRate x regionFactor x sumInsuredFactor x claimsLoading
 *         -&gt; raised to MINIMUM_PREMIUM if below it
 *         -&gt; rounded to 2dp, HALF_UP
 * </pre>
 *
 * <p>Rounding happens <strong>once, at the very end</strong>. Rounding intermediate factors
 * would let error accumulate across four multiplications and make the quoted premium depend
 * on the order they were applied in.
 */
public final class PremiumCalculator {

    /** Scale of a quoted premium: minor units of {@link RateTable#CURRENCY}. */
    private static final int PREMIUM_SCALE = 2;

    private PremiumCalculator() {
        // Stateless calculator; not intended for instantiation.
    }

    /**
     * Calculates the premium payable for the given attributes.
     *
     * @param attributes the policy inputs, never null
     * @return the premium and the factors that produced it
     */
    public static PremiumCalculation calculate(PolicyAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes must not be null");

        BigDecimal baseRate = RateTable.baseRate(attributes.coverageType());
        BigDecimal regionFactor = RateTable.regionFactor(attributes.region());
        BigDecimal sumInsuredFactor = RateTable.sumInsuredFactor(attributes.sumInsured());
        BigDecimal claimsLoading = RateTable.claimsLoading(attributes.priorClaimsCount());

        BigDecimal calculated =
                baseRate.multiply(regionFactor).multiply(sumInsuredFactor).multiply(claimsLoading);

        // The floor is compared against the unrounded figure, so a premium a fraction below
        // the minimum is treated as below it rather than being rounded up to meet it.
        boolean minimumApplied = calculated.compareTo(RateTable.MINIMUM_PREMIUM) < 0;
        BigDecimal payable = minimumApplied ? RateTable.MINIMUM_PREMIUM : calculated;

        PremiumBreakdown breakdown = new PremiumBreakdown(
                baseRate, regionFactor, sumInsuredFactor, claimsLoading, calculated, minimumApplied);

        return new PremiumCalculation(
                payable.setScale(PREMIUM_SCALE, RoundingMode.HALF_UP), RateTable.CURRENCY, breakdown);
    }
}
