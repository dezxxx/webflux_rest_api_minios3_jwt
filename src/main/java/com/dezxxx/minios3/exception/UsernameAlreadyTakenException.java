package com.dezxxx.minios3.exception;

/** Thrown when registration hits the unique constraint on users.username. */
public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String username) {
        super("Username already taken: " + username);
    }
}
