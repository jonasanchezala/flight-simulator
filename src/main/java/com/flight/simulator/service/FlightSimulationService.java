package com.flight.simulator.service;

import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Computes a realistic {@link FlightMetrics} snapshot for a given elapsed
 * simulated time.
 * <p>
 * Route: LAX (33.9425°N, 118.4081°W) → JFK (40.6413°N, 73.7781°W)
 */
@Component
@RequiredArgsConstructor
public class FlightSimulationService {

    private final PhaseService phaseService;

    private static final double LAX_LAT = 33.9425;
    private static final double LAX_LON = -118.4081;
    private static final double JFK_LAT = 40.6413;
    private static final double JFK_LON = -73.7781;

    private static final double MAX_ALTITUDE_FEET = 37_000;
    private static final double CRUISE_SPEED_KNOTS = 480;
    private static final double INITIAL_FUEL_PCT = 100.0;
    private static final double FUEL_BURN_PER_MIN = 0.28;
    private static final double TROPOPAUSE_TEMP_C = -56.5;

    public FlightMetrics compute(Integer flightId, double elapsedMinutes) {
        FlightPhase phase = phaseService.resolve(elapsedMinutes);

        double altitude = computeAltitude(phase, elapsedMinutes);
        double airspeed = computeAirspeed(phase, elapsedMinutes);
        double heading = computeHeading(phase, elapsedMinutes);
        double[] pos = computePosition(elapsedMinutes);
        double fuel = computeFuel(elapsedMinutes);
        double oat = computeOAT(altitude);
        double eta = computeETA(elapsedMinutes);

        return FlightMetrics.builder()
                .flightId(flightId)
                .recordedAt(Instant.now())
                .phase(phase)
                .altitudeFeet(round(altitude))
                .airspeedKnots(round(airspeed))
                .headingDegrees(round(heading))
                .latitude(round(pos[0]))
                .longitude(round(pos[1]))
                .fuelPercentage(round(fuel))
                .outsideAirTempCelsius(round(oat))
                .etaMinutes(round(eta))
                .build();
    }

    private double computeAltitude(FlightPhase phase, double elapsed) {
        return switch (phase) {
            case BOARDING, TAXI_OUT, LANDING, TAXI_IN, COMPLETED -> 0;
            case TAKEOFF_CLIMB -> {
                double progress = phaseProgress(phase, elapsed);
                yield MAX_ALTITUDE_FEET * progress;
            }
            case CRUISE -> MAX_ALTITUDE_FEET;
            case DESCENT -> {
                double progress = phaseProgress(phase, elapsed);
                yield MAX_ALTITUDE_FEET * (1.0 - progress);
            }
        };
    }

    private double computeAirspeed(FlightPhase phase, double elapsed) {
        return switch (phase) {
            case BOARDING, COMPLETED -> 0;
            case TAXI_OUT -> 15;
            case TAKEOFF_CLIMB -> {
                double progress = phaseProgress(phase, elapsed);
                yield 160 + (CRUISE_SPEED_KNOTS - 160) * progress;
            }
            case CRUISE -> CRUISE_SPEED_KNOTS;
            case DESCENT -> {
                double progress = phaseProgress(phase, elapsed);
                yield CRUISE_SPEED_KNOTS * (1.0 - progress * 0.5);
            }
            case LANDING -> {
                double progress = phaseProgress(phase, elapsed);
                yield 240 * (1.0 - progress);
            }
            case TAXI_IN -> 10;
        };
    }

    private double computeHeading(FlightPhase phase, double elapsed) {
        if (phase == FlightPhase.BOARDING || phase == FlightPhase.COMPLETED) return 0;
        return 66.0 + variation(elapsed);  // LAX→JFK great-circle ≈ 66°
    }

    private double[] computePosition(double elapsed) {
        double routeStart = phaseService.startMinuteOf(FlightPhase.BOARDING)
                + phaseService.durationOf(FlightPhase.BOARDING);
        double routeEnd = phaseService.totalDurationMinutes()
                - phaseService.durationOf(FlightPhase.TAXI_IN);

        double progress = clamp((elapsed - routeStart) / (routeEnd - routeStart));
        return new double[]{
                LAX_LAT + (JFK_LAT - LAX_LAT) * progress,
                LAX_LON + (JFK_LON - LAX_LON) * progress
        };
    }

    private double computeFuel(double elapsed) {
        return Math.max(0, INITIAL_FUEL_PCT - elapsed * FUEL_BURN_PER_MIN);
    }

    private double computeOAT(double altitudeFeet) {
        if (altitudeFeet <= 0) return 20.0;
        return Math.max(TROPOPAUSE_TEMP_C, 15.0 - (altitudeFeet / 1000.0) * 2.0);
    }

    private double computeETA(double elapsed) {
        double airborneStart = phaseService.startMinuteOf(FlightPhase.TAKEOFF_CLIMB);
        double airborneEnd = phaseService.startMinuteOf(FlightPhase.LANDING)
                + phaseService.durationOf(FlightPhase.LANDING);
        if (elapsed < airborneStart) return airborneEnd - airborneStart;
        if (elapsed >= airborneEnd) return 0;
        return airborneEnd - elapsed;
    }

    /**
     * Progress through the given phase as a value in [0, 1].
     */
    private double phaseProgress(FlightPhase phase, double elapsed) {
        return clamp((elapsed - phaseService.startMinuteOf(phase))
                / (double) phaseService.durationOf(phase));
    }

    private double variation(double elapsed) {
        return (double) 3 * Math.sin(elapsed * 0.3) * 0.1;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
