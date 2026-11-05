package com.wealthmax.common.exception;

/** The thing asked for does not exist. Answered with 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
