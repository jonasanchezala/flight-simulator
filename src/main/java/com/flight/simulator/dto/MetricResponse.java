package com.flight.simulator.dto;

import com.flight.simulator.model.FlightPhase;
import lombok.Builder;

import java.time.Instant;

@Builder
public record MetricResponse(Integer id,
                             Integer flightId,
                             Instant recordedAt,
                             FlightPhase phase,
                             double altitudeFeet,
                             double airspeedKnots,
                             double headingDegrees,
                             double latitude,
                             double longitude,
                             double fuelPercentage,
                             double outsideAirTempCelsius,
                             double etaMinutes,
                             int timeMultiplier) {
}