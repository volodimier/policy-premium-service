package com.example.policypremium.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the published API.
 *
 * <p>Without this the generated document is titled "OpenAPI definition" at version "v0",
 * which tells a consumer nothing about what they are calling.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI policyPremiumOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Policy Premium Service")
                                .version("v1")
                                .description(
                                        """
                                Calculates policy premiums from a small set of attributes and stores \
                                the result so it can be retrieved later.

                                A quote is immutable once issued: retrieving one returns the numbers \
                                it was calculated with, never a fresh calculation. Every response \
                                includes a factor-by-factor breakdown so a premium can be audited \
                                without re-running the rules.

                                All amounts are in SEK.\
                                """));
    }
}
