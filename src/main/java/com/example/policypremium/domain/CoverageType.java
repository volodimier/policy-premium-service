package com.example.policypremium.domain;

/**
 * The kinds of cover this service can price.
 *
 * <p>Deliberately small. Rates are not held here - see {@code RateTable} - so that pricing
 * policy can change without touching the domain vocabulary.
 */
public enum CoverageType {
    HOME,
    AUTO,
    LIABILITY
}
