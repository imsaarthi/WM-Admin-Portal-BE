package com.wealthmax.common.exception;

/** The request is well formed but breaks a business rule (a bad date range, an unknown sort field). Answered with 400. */
public class InvalidDataException extends RuntimeException {

    public InvalidDataException(String message) {
        super(message);
    }
}
