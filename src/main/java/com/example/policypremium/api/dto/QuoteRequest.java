package com.example.policypremium.api.dto;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.PolicyAttributes;
import com.example.policypremium.domain.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Inputs accepted by {@code POST /api/v1/quotes}.
 *
 * <p>Boxed types are used so a missing field fails as {@code @NotNull} with a field-level
 * message, rather than silently defaulting to zero.
 *
 * <p>These constraints shape the HTTP contract. The domain enforces its own invariants
 * separately, so a {@link PolicyAttributes} cannot be constructed in a nonsensical state
 * even by a caller that bypasses this layer.
 */
public record QuoteRequest(
        @Schema(example = "AUTO") @NotNull(message = "coverageType is required") CoverageType coverageType,
        @Schema(example = "750000", description = "Amount insured, in SEK")
                @NotNull(message = "sumInsured is required")
                @DecimalMin(value = "0", inclusive = false, message = "sumInsured must be greater than zero")
                @DecimalMax(value = "100000000", message = "sumInsured must not exceed 100000000")
                BigDecimal sumInsured,
        @Schema(example = "MALMO") @NotNull(message = "region is required") Region region,
        @Schema(example = "2", description = "Claims in the prior period; loading is capped at 4")
                @NotNull(message = "priorClaimsCount is required")
                @Min(value = 0, message = "priorClaimsCount must not be negative")
                @Max(value = 50, message = "priorClaimsCount must not exceed 50")
                Integer priorClaimsCount) {

    /** Converts to the domain type the rules operate on. */
    public PolicyAttributes toAttributes() {
        return new PolicyAttributes(coverageType, sumInsured, region, priorClaimsCount);
    }
}
