package com.example.policypremium;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PolicyPremiumServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyPremiumServiceApplication.class, args);
    }

    /**
     * The application clock.
     *
     * <p>Injected rather than calling {@code Instant.now()} at the point of use, so tests can
     * substitute a fixed clock and assert on timestamps instead of merely asserting that one
     * exists.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
