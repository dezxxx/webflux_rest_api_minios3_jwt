package com.dezxxx.minios3.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Integer id) {
        super("User not found: " + id);
    }

    public UserNotFoundException(String username) {
        super("User not found: " + username);
    }
}
