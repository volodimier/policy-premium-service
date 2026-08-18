package com.example.policypremium.api.dto;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.PolicyAttributes;
import com.example.policypremium.domain.Region;
import java.math.BigDecimal;

/**
 * The attributes a quote was priced from, echoed back so a stored quote is self-describing.
 *
 * <p>Separate from {@link QuoteRequest} on purpose. Returning the request type would put
 * inbound validation constraints on an outbound payload and tie the two contracts together,
 * so that tightening a request rule would silently reshape responses.
 *
 * @param coverageType kind of cover
 * @param sumInsured amount insured
 * @param region rating region
 * @param priorClaimsCount claims in the prior period
 */
public record PolicyAttributesResponse(
        CoverageType coverageType, BigDecimal sumInsured, Region region, int priorClaimsCount) {

    /** Maps domain attributes onto the published response shape. */
    public static PolicyAttributesResponse from(PolicyAttributes attributes) {
        return new PolicyAttributesResponse(
                attributes.coverageType(), attributes.sumInsured(), attributes.region(), attributes.priorClaimsCount());
    }
}
