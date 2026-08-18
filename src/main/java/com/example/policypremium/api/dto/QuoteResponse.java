package com.example.policypremium.api.dto;

import com.example.policypremium.domain.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A calculated quote as returned to callers.
 *
 * <p>Built from a stored {@link Quote}, so {@code POST} and {@code GET} return an identical
 * shape and a caller never has to special-case which one they used.
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
        PolicyAttributesResponse input,
        BreakdownResponse breakdown,
        Instant createdAt) {

    /** Maps a stored quote onto the published response shape. */
    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.id(),
                quote.calculation().premium(),
                quote.calculation().currency(),
                PolicyAttributesResponse.from(quote.attributes()),
                BreakdownResponse.from(quote.calculation().breakdown()),
                quote.createdAt());
    }
}
