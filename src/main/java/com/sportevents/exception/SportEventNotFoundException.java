package com.sportevents.exception;

public class SportEventNotFoundException extends RuntimeException {
    public SportEventNotFoundException(String id) {
        super("Sport event not found with id: " + id);
    }
}
