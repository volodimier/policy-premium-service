package com.example.policypremium.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice tests for the HTTP contract: status codes, headers, response shape and error
 * bodies. The pricing itself is covered exhaustively by the rules tests, so these assert
 * only enough of the numbers to prove the wiring is right.
 */
@WebMvcTest(QuoteController.class)
@Import(GlobalExceptionHandler.class)
class QuoteControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-18T12:00:00Z");

    /**
     * Replaces the application clock with a fixed one, so {@code createdAt} is an exact value
     * to assert rather than something we can only check for existence.
     */
    @TestBean
    private Clock clock;

    static Clock clock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    @Autowired
    private MockMvc mockMvc;

    private static String body(String coverageType, String sumInsured, String region, String claims) {
        return """
                {
                  "coverageType": %s,
                  "sumInsured": %s,
                  "region": %s,
                  "priorClaimsCount": %s
                }
                """
                .formatted(coverageType, sumInsured, region, claims);
    }

    @Nested
    @DisplayName("successful quotes")
    class HappyPath {

        @Test
        void returnCreatedWithTheQuoteAndALocationHeader() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"AUTO\"", "750000", "\"MALMO\"", "2")))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.premium").value(4402.20))
                    .andExpect(jsonPath("$.currency").value("SEK"))
                    .andExpect(jsonPath("$.createdAt").value(FIXED_NOW.toString()));
        }

        @Test
        void locationHeaderPointsAtTheCreatedQuote() throws Exception {
            String location = mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", "300000", "\"STOCKHOLM\"", "0")))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getHeader("Location");

            org.assertj.core.api.Assertions.assertThat(location)
                    .contains("/api/v1/quotes/")
                    .matches(".*/api/v1/quotes/[0-9a-f-]{36}$");
        }

        @Test
        void echoTheInputSoTheQuoteIsSelfDescribing() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", "300000", "\"STOCKHOLM\"", "0")))
                    .andExpect(jsonPath("$.input.coverageType").value("HOME"))
                    .andExpect(jsonPath("$.input.sumInsured").value(300000))
                    .andExpect(jsonPath("$.input.region").value("STOCKHOLM"))
                    .andExpect(jsonPath("$.input.priorClaimsCount").value(0));
        }

        @Test
        void exposeEveryFactorInTheBreakdown() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"AUTO\"", "750000", "\"MALMO\"", "2")))
                    .andExpect(jsonPath("$.breakdown.baseRate").value(2400))
                    .andExpect(jsonPath("$.breakdown.regionFactor").value(1.10))
                    .andExpect(jsonPath("$.breakdown.sumInsuredFactor").value(1.15))
                    .andExpect(jsonPath("$.breakdown.claimsLoading").value(1.45))
                    .andExpect(jsonPath("$.breakdown.calculatedPremium").value(4402.20))
                    .andExpect(jsonPath("$.breakdown.minimumPremiumApplied").value(false));
        }

        @Test
        void flagWhenTheMinimumPremiumWasApplied() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"LIABILITY\"", "100000", "\"NORTH\"", "0")))
                    .andExpect(jsonPath("$.premium").value(750.00))
                    .andExpect(jsonPath("$.breakdown.calculatedPremium").value(720.00))
                    .andExpect(jsonPath("$.breakdown.minimumPremiumApplied").value(true));
        }
    }

    @Nested
    @DisplayName("validation failures")
    class Validation {

        @Test
        void rejectAMissingCoverageType() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("null", "300000", "\"STOCKHOLM\"", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.errors.coverageType").value("coverageType is required"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "100000001"})
        void rejectAnOutOfRangeSumInsured(String sumInsured) throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", sumInsured, "\"STOCKHOLM\"", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.sumInsured").isNotEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "51"})
        void rejectAnOutOfRangePriorClaimsCount(String claims) throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", "300000", "\"STOCKHOLM\"", claims)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.priorClaimsCount").isNotEmpty());
        }

        @Test
        void reportEveryFailingFieldAtOnce() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("null", "-5", "null", "99")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.coverageType").isNotEmpty())
                    .andExpect(jsonPath("$.errors.sumInsured").isNotEmpty())
                    .andExpect(jsonPath("$.errors.region").isNotEmpty())
                    .andExpect(jsonPath("$.errors.priorClaimsCount").isNotEmpty());
        }

        @Test
        void acceptTheBoundaryValues() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", "100000000", "\"OTHER\"", "50")))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("unparseable requests")
    class Unparseable {

        /** An unknown enum fails during deserialisation, before validation ever runs. */
        @Test
        void rejectAnUnknownCoverageTypeAsBadRequestNotServerError() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"MOTORCYCLE\"", "300000", "\"STOCKHOLM\"", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request"))
                    .andExpect(
                            jsonPath("$.accepted.coverageType").value(containsInAnyOrder("HOME", "AUTO", "LIABILITY")));
        }

        @Test
        void rejectAnUnknownRegion() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("\"HOME\"", "300000", "\"UPPSALA\"", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.accepted.region").isNotEmpty());
        }

        @Test
        void rejectMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/quotes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"coverageType\": \"HOME\", "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request"));
        }
    }
}
