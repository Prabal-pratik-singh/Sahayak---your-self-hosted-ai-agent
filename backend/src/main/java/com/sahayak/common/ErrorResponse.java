package com.sahayak.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every error the API returns has this shape. {@code error} is always present;
 * {@code provider} and {@code category} appear only for AI-provider failures
 * so the UI can say which brain failed and why.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String provider, String category) {

    public ErrorResponse(String error) {
        this(error, null, null);
    }
}
