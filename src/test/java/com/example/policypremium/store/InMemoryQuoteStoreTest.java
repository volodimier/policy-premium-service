package com.example.policypremium.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.example.policypremium.domain.CoverageType;
import com.example.policypremium.domain.PolicyAttributes;
import com.example.policypremium.domain.Quote;
import com.example.policypremium.domain.Region;
import com.example.policypremium.rules.PremiumCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryQuoteStoreTest {

    private final QuoteStore store = new InMemoryQuoteStore();

    private static Quote quote() {
        PolicyAttributes attributes =
                new PolicyAttributes(CoverageType.HOME, new BigDecimal("300000"), Region.STOCKHOLM, 0);
        return new Quote(
                UUID.randomUUID(),
                attributes,
                PremiumCalculator.calculate(attributes),
                Instant.parse("2026-08-18T12:00:00Z"));
    }

    @Test
    void returnsAStoredQuoteUnchanged() {
        Quote saved = quote();
        store.save(saved);

        assertThat(store.findById(saved.id())).contains(saved);
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void returnsEmptyRatherThanFailingForANullId() {
        assertThat(store.findById(null)).isEmpty();
    }

    @Test
    void rejectsANullQuote() {
        assertThatNullPointerException().isThrownBy(() -> store.save(null));
    }

    @Test
    void refusesToOverwriteAnExistingQuote() {
        Quote first = quote();
        store.save(first);
        Quote sameId = new Quote(first.id(), first.attributes(), first.calculation(), Instant.now());

        assertThatIllegalStateException().isThrownBy(() -> store.save(sameId));
        assertThat(store.findById(first.id())).contains(first);
    }

    @Test
    void keepsQuotesSeparate() {
        Quote one = quote();
        Quote two = quote();
        store.save(one);
        store.save(two);

        assertThat(store.findById(one.id())).contains(one);
        assertThat(store.findById(two.id())).contains(two);
    }

    /**
     * A web container serves requests on many threads, so concurrent writes are the normal
     * case rather than an edge case. Every quote written must be readable afterwards.
     */
    @Test
    void storesConcurrentWritesWithoutLoss() throws Exception {
        int writers = 50;
        List<Quote> quotes = IntStream.range(0, writers).mapToObj(i -> quote()).toList();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Quote>> tasks = quotes.stream()
                    .map(q -> (Callable<Quote>) () -> store.save(q))
                    .toList();
            executor.invokeAll(tasks);
        }

        assertThat(quotes).allSatisfy(q -> assertThat(store.findById(q.id())).contains(q));
    }
}
