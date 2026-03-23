package com.flight.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("flight_metrics")
public class FlightMetrics {

    @Id
    private Integer id;
    private Integer flightId;
    private Instant recordedAt;

    private FlightPhase phase;
    private double altitudeFeet;
    private double airspeedKnots;
    private double headingDegrees;
    private double latitude;
    private double longitude;
    private double fuelPercentage;
    private double outsideAirTempCelsius;
    private double etaMinutes;
}
