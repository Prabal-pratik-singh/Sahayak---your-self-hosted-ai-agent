package com.sahayak.common;

/** Every error the API returns has this one shape: { "error": "human readable message" } */
public record ErrorResponse(String error) {
}
