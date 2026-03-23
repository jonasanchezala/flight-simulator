package com.flight.simulator.service;

import com.flight.simulator.model.FlightPhase;

public interface PhaseService {
    FlightPhase resolve(double elapsedMinutes);

    int totalDurationMinutes();

    int startMinuteOf(FlightPhase phase);

    int durationOf(FlightPhase phase);
}