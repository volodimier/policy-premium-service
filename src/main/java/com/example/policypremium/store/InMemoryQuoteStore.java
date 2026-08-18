package com.example.policypremium.store;

import com.example.policypremium.domain.Quote;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Holds quotes in memory for the lifetime of the process.
 *
 * <p>Backed by a {@link ConcurrentHashMap} because a web container serves requests on many
 * threads; a plain {@code HashMap} here would be a data race waiting to happen under any
 * real concurrency.
 *
 * <p>Quotes are immutable records, so nothing needs defensive copying on the way in or out.
 *
 * <p><strong>Contents are lost on restart and are not shared between instances.</strong>
 * Acceptable for this service's scope, and the reason {@link QuoteStore} exists as an
 * interface.
 */
@Repository
public class InMemoryQuoteStore implements QuoteStore {

    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();

    @Override
    public Quote save(Quote quote) {
        Objects.requireNonNull(quote, "quote must not be null");
        Quote existing = quotes.putIfAbsent(quote.id(), quote);
        if (existing != null) {
            throw new IllegalStateException("A quote already exists with id " + quote.id());
        }
        return quote;
    }

    @Override
    public Optional<Quote> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(quotes.get(id));
    }
}
