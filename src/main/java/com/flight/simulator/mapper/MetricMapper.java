package com.flight.simulator.mapper;

import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.model.FlightMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricMapper {

    public MetricResponse toResponse(FlightMetrics flightMetrics) {
        return MetricResponse.builder()
                .id(flightMetrics.getId())
                .flightId(flightMetrics.getFlightId())
                .recordedAt(flightMetrics.getRecordedAt())
                .phase(flightMetrics.getPhase())
                .altitudeFeet(flightMetrics.getAltitudeFeet())
                .airspeedKnots(flightMetrics.getAirspeedKnots())
                .headingDegrees(flightMetrics.getHeadingDegrees())
                .latitude(flightMetrics.getLatitude())
                .longitude(flightMetrics.getLongitude())
                .fuelPercentage(flightMetrics.getFuelPercentage())
                .outsideAirTempCelsius(flightMetrics.getOutsideAirTempCelsius())
                .etaMinutes(flightMetrics.getEtaMinutes())
                .build();
    }
}