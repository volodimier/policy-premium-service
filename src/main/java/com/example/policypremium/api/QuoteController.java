package com.example.policypremium.api;

import com.example.policypremium.api.dto.QuoteRequest;
import com.example.policypremium.api.dto.QuoteResponse;
import com.example.policypremium.rules.PremiumCalculation;
import com.example.policypremium.rules.PremiumCalculator;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP entry point for premium quotes. */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final Clock clock;

    /**
     * @param clock injected rather than calling {@code Instant.now()} directly, so timestamps
     *     are deterministic under test
     */
    public QuoteController(Clock clock) {
        this.clock = clock;
    }

    /**
     * Calculates a premium for the supplied policy attributes.
     *
     * @param request the policy attributes, validated against the published constraints
     * @param uriBuilder used to build the {@code Location} header
     * @return {@code 201 Created} with the quote and a {@code Location} pointing at it
     */
    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(
            @Valid @RequestBody QuoteRequest request, UriComponentsBuilder uriBuilder) {

        PremiumCalculation calculation = PremiumCalculator.calculate(request.toAttributes());
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now(clock);

        QuoteResponse body = QuoteResponse.from(id, request, calculation, createdAt);
        URI location = uriBuilder.path("/api/v1/quotes/{id}").buildAndExpand(id).toUri();

        return ResponseEntity.created(location).body(body);
    }
}
