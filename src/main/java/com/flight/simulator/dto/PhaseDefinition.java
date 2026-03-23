package com.flight.simulator.dto;

import com.flight.simulator.model.FlightPhase;

public record PhaseDefinition(FlightPhase phase, int durationMinutes) {
}