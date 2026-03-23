package com.flight.simulator.exception;

public class FlightNotFoundException extends RuntimeException {
    public FlightNotFoundException(Integer id) {
        super("Flight not found: " + id);
    }
}