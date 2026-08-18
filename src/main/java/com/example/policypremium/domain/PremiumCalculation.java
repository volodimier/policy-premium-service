package com.example.policypremium.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The result of pricing a policy: the payable premium and how it was reached.
 *
 * <p>Deliberately not a {@code Quote} - a quote is a stored, identified thing with a
 * lifetime, while this is the pure output of a calculation. Keeping them separate lets the
 * calculator stay a function of its inputs with no knowledge of persistence or identity.
 *
 * <p>Lives in the domain rather than alongside the calculator so that {@code Quote} can hold
 * one without the domain depending on the rules package.
 *
 * @param premium payable premium, rounded to 2dp
 * @param currency ISO 4217 code of {@link #premium}
 * @param breakdown the factors that produced it
 */
public record PremiumCalculation(BigDecimal premium, String currency, PremiumBreakdown breakdown) {

    public PremiumCalculation {
        Objects.requireNonNull(premium, "premium must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(breakdown, "breakdown must not be null");
    }
}
