package com.example.policypremium.domain;

/**
 * Geographic rating regions.
 *
 * <p>{@link #OTHER} is the catch-all so that a valid quote can always be produced for an
 * address outside the named regions.
 */
public enum Region {
    STOCKHOLM,
    GOTEBORG,
    MALMO,
    NORTH,
    OTHER
}
