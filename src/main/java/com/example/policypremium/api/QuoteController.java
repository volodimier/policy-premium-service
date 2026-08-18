package com.example.policypremium.api;

import com.example.policypremium.api.dto.QuoteRequest;
import com.example.policypremium.api.dto.QuoteResponse;
import com.example.policypremium.domain.PremiumCalculation;
import com.example.policypremium.domain.Quote;
import com.example.policypremium.rules.PremiumCalculator;
import com.example.policypremium.store.QuoteStore;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
    @GetMapping("/{id}")
    public QuoteResponse getQuote(@PathVariable UUID id) {
        return quoteStore.findById(id).map(QuoteResponse::from).orElseThrow(() -> new QuoteNotFoundException(id));
    }
}
