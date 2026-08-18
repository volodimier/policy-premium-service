package com.example.policypremium.store;

import com.example.policypremium.domain.Quote;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for issued quotes.
 *
 * <p>An interface with a single in-memory implementation. That is deliberate: persistence is
 * out of scope here, but the seam where a database would go should be visible rather than
 * implied. Swapping in a real store means adding an implementation, not rewriting callers.
 *
 * <p>Returns {@link Optional} rather than null or an exception, so absence is part of the
 * contract and callers decide what it means - here, a 404.
 */
public interface QuoteStore {

    /**
     * Stores a quote, which must not already exist.
     *
     * @param quote the quote to store
     * @return the stored quote
     */
    Quote save(Quote quote);

    /**
     * Finds a quote by its identifier.
     *
     * @param id the identifier
     * @return the quote, or empty if no quote has that id
     */
    Optional<Quote> findById(UUID id);
}
