package com.example.policypremium.api;

import java.util.UUID;

/** Raised when a caller asks for a quote id that was never issued, or has since been lost. */
public class QuoteNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    public QuoteNotFoundException(UUID id) {
        super("No quote found with id " + id);
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
