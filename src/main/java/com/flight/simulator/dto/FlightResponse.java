package com.flight.simulator.dto;

import com.flight.simulator.model.FlightPhase;
import lombok.Builder;

import java.time.Instant;

@Builder
public record FlightResponse(Integer id,
                             boolean active,
                             String origin,
                             String destination,
                             String flightNumber,
                             String airline,
                             FlightPhase phase,
                             Instant startedAt,
                             Instant completedAt,
                             int timeMultiplier) {
}