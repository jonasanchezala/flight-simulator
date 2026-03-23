package com.flight.simulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlightRequest(
        @NotBlank
        @Size(min = 3, max = 4, message = "origin must be an IATA/ICAO code")
        String origin,
        @NotBlank
        @Size(min = 3, max = 4, message = "destination must be an IATA/ICAO code")
        String destination,
        @NotBlank String flightNumber,
        @NotBlank String airline,
        Integer timeMultiplier
) {
}
