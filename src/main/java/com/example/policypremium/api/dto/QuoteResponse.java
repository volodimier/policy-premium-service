package com.example.policypremium.api.dto;

import com.example.policypremium.rules.PremiumCalculation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A calculated quote as returned to callers.
 *
 * <p>The request is echoed back so a stored quote is self-describing: a caller fetching one
 * later can see what it was priced from without holding on to their original payload.
 *
 * @param id server-assigned identifier, usable with {@code GET /api/v1/quotes/{id}}
 * @param premium payable premium, always at 2dp
 * @param currency ISO 4217 code of {@link #premium}
 * @param input the attributes this quote was calculated from
 * @param breakdown the factors that produced the premium
 * @param createdAt when the quote was calculated
 */
public record QuoteResponse(
        UUID id,
        BigDecimal premium,
        String currency,
        QuoteRequest input,
        BreakdownResponse breakdown,
        Instant createdAt) {

    /** Assembles a response from a calculation and the request that produced it. */
    public static QuoteResponse from(UUID id, QuoteRequest request, PremiumCalculation calculation, Instant createdAt) {
        return new QuoteResponse(
                id,
                calculation.premium(),
                calculation.currency(),
                request,
                BreakdownResponse.from(calculation.breakdown()),
                createdAt);
    }
}
