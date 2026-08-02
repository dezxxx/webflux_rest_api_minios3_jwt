package com.dezxxx.minios3.exception;

/** The presented token is malformed, forged, or not of the expected type.
 *  Expiry is a separate case — see {@link ExpiredTokenException}. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}