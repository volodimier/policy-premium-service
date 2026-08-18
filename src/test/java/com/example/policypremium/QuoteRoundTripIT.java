package com.example.policypremium;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * End-to-end test over real HTTP against the running application.
 *
 * <p>The web-slice tests cover the contract in detail; this exists to prove the pieces are
 * wired together - that a quote created by one request is retrievable by another, with the
 * same numbers.
 *
 * <p>Uses the JDK HTTP client rather than a framework test client: the point is to exercise
 * the real server over a socket, and doing so needs no extra dependency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteRoundTripIT {

    private static final String REQUEST =
            """
            {
              "coverageType": "AUTO",
              "sumInsured": 750000,
              "region": "MALMO",
              "priorClaimsCount": 2
            }
            """;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(url(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("a created quote is retrievable by id with identical numbers")
    void createThenRetrieve() throws Exception {
        HttpResponse<String> created = post("/api/v1/quotes", REQUEST);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<Double>read(created.body(), "$.premium")).isEqualTo(4402.20);

        String id = JsonPath.read(created.body(), "$.id");
        URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
        assertThat(location.getPath()).isEqualTo("/api/v1/quotes/" + id);

        HttpResponse<String> fetched = get(location);

        assertThat(fetched.statusCode()).isEqualTo(200);
        // Byte-identical: a retrieved quote is the stored one, not a fresh calculation.
        assertThat(fetched.body()).isEqualTo(created.body());
    }

    @Test
    @DisplayName("a retrieved quote keeps the numbers it was issued with")
    void retrievedQuoteKeepsItsBreakdown() throws Exception {
        HttpResponse<String> created = post("/api/v1/quotes", REQUEST);
        HttpResponse<String> fetched =
                get(URI.create(created.headers().firstValue("Location").orElseThrow()));

        assertThat(JsonPath.<Double>read(fetched.body(), "$.breakdown.calculatedPremium"))
                .isEqualTo(4402.20);
        assertThat(JsonPath.<Boolean>read(fetched.body(), "$.breakdown.minimumPremiumApplied"))
                .isFalse();
        assertThat(JsonPath.<String>read(fetched.body(), "$.currency")).isEqualTo("SEK");
    }

    @Test
    void unknownIdReturnsNotFound() throws Exception {
        HttpResponse<String> response = get(url("/api/v1/quotes/" + UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.body(), "$.title")).isEqualTo("Quote not found");
    }

    /**
     * Content-type negotiation on an error path is one of the few things a real container can
     * get wrong where a mock cannot, so it is asserted here rather than only in the slice.
     */
    @Test
    void invalidRequestReturnsProblemDetail() throws Exception {
        HttpResponse<String> response = post("/api/v1/quotes", REQUEST.replace("750000", "-1"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(JsonPath.<String>read(response.body(), "$.errors.sumInsured"))
                .isNotBlank();
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get(url("/actuator/health"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
    }
}
