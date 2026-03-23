package com.flight.simulator.mapper;

import com.flight.simulator.config.SimulationProperties;
import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.model.Flight;
import com.flight.simulator.model.FlightPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FlightMapper {

    private final SimulationProperties props;

    public Flight toDomain(FlightRequest request) {
        int multiplier = (request.timeMultiplier() != null && request.timeMultiplier() > 0)
                ? request.timeMultiplier()
                : props.getTimeMultiplier();

        return Flight.builder()
                .origin(request.origin().toUpperCase())
                .destination(request.destination().toUpperCase())
                .airline(request.airline())
                .flightNumber(request.flightNumber())
                .phase(FlightPhase.BOARDING)
                .startedAt(Instant.now())
                .timeMultiplier(multiplier)
                .build();
    }

    public FlightResponse toResponse(Flight flight) {
        return FlightResponse.builder()
                .id(flight.getId())
                .active(flight.isActive())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .airline(flight.getAirline())
                .flightNumber(flight.getFlightNumber())
                .phase(flight.getPhase())
                .startedAt(flight.getStartedAt())
                .completedAt(flight.getCompletedAt())
                .timeMultiplier(flight.getTimeMultiplier())
                .build();
    }
}