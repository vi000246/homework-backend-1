package com.example.demo.exception;

/** Raised when an optimistic-lock version check fails (concurrent modification). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
