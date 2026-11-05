package com.wealthmax.common.exception;

/**
 * The caller could not be authenticated (wrong credentials, bad or expired code). Answered with 401.
 * Keep the message the same whether or not an account exists, so it cannot be used to find accounts.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
