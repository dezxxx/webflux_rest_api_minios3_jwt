package com.dezxxx.minios3.exception;

/**
 * Also raised when the event exists but belongs to somebody else: the caller must not be
 * able to tell those two cases apart.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Integer id) {
        super("Event not found: " + id);
    }
}
