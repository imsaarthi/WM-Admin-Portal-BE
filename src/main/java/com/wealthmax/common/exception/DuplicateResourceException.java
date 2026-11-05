package com.wealthmax.common.exception;

/** The thing already exists, or a rule such as a cooling period forbids creating it. Answered with 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
