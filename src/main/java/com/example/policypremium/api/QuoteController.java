package com.example.policypremium.api;

import com.example.policypremium.api.dto.QuoteRequest;
import com.example.policypremium.api.dto.QuoteResponse;
import com.example.policypremium.domain.PremiumCalculation;
import com.example.policypremium.domain.Quote;
import com.example.policypremium.rules.PremiumCalculator;
import com.example.policypremium.store.QuoteStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP entry point for premium quotes. */
@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Quotes", description = "Calculate premiums and retrieve previously issued quotes")
public class QuoteController {

    private final QuoteStore quoteStore;
    private final Clock clock;

    /**
     * @param quoteStore where issued quotes are kept
     * @param clock injected rather than calling {@code Instant.now()} directly, so timestamps
     *     are deterministic under test
     */
    public QuoteController(QuoteStore quoteStore, Clock clock) {
        this.quoteStore = quoteStore;
        this.clock = clock;
    }

    /**
     * Calculates a premium for the supplied policy attributes and stores the result.
     *
     * @param request the policy attributes, validated against the published constraints
     * @param uriBuilder used to build the {@code Location} header
     * @return {@code 201 Created} with the quote and a {@code Location} pointing at it
     */
    @Operation(
            summary = "Calculate a premium",
            description = "Prices the supplied attributes, stores the result and returns it with a "
                    + "Location header pointing at the stored quote.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Quote calculated and stored",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = QuoteResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "A constraint was violated, or the body could not be parsed - for "
                        + "example an unknown coverage type or region",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(
            @Valid @RequestBody QuoteRequest request, UriComponentsBuilder uriBuilder) {

        PremiumCalculation calculation = PremiumCalculator.calculate(request.toAttributes());
        Quote quote = new Quote(UUID.randomUUID(), request.toAttributes(), calculation, Instant.now(clock));
        quoteStore.save(quote);

        URI location = uriBuilder
                .path("/api/v1/quotes/{id}")
                .buildAndExpand(quote.id())
                .toUri();

        return ResponseEntity.created(location).body(QuoteResponse.from(quote));
    }

    /**
     * Retrieves a previously calculated quote.
     *
     * <p>Returns the numbers the quote was issued with, not a fresh calculation, so a quote
     * cannot change price after the fact.
     *
     * @param id the quote identifier
     * @return {@code 200 OK} with the quote
     * @throws QuoteNotFoundException if no quote has that id
     */
    @Operation(
            summary = "Retrieve a quote",
            description = "Returns the numbers the quote was issued with. Nothing is recalculated, "
                    + "so a quote cannot change price after the fact.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The stored quote",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = QuoteResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "The id is not a UUID",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "No quote exists with that id",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public QuoteResponse getQuote(@PathVariable UUID id) {
        return quoteStore.findById(id).map(QuoteResponse::from).orElseThrow(() -> new QuoteNotFoundException(id));
    }
}
