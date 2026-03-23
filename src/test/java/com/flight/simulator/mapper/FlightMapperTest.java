package com.flight.simulator.mapper;

import com.flight.simulator.config.SimulationProperties;
import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.model.Flight;
import com.flight.simulator.model.FlightPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightMapperTest {

    @Mock
    private SimulationProperties props;
    @InjectMocks
    private FlightMapper mapper;


    @Test
    void testToDomainUppercasesOriginAndDestination() {
        FlightRequest req = request("lax", "jfk", null);

        Flight result = mapper.toDomain(req);

        assertThat(result.getOrigin()).isEqualTo("LAX");
        assertThat(result.getDestination()).isEqualTo("JFK");
    }

    @Test
    void testToDomainMapsAirlineAndFlightNumber() {
        FlightRequest req = request("LAX", "JFK", null);
        Flight result = mapper.toDomain(req);

        assertThat(result.getAirline()).isEqualTo("Test Air");
        assertThat(result.getFlightNumber()).isEqualTo("TA001");
    }

    @Test
    void testToDomainPhaseIsBoarding() {
        Flight result = mapper.toDomain(request("LAX", "JFK", null));
        assertThat(result.getPhase()).isEqualTo(FlightPhase.BOARDING);
    }

    @Test
    void testToDomainUsesRequestMultiplier() {
        FlightRequest req = request("LAX", "JFK", 300);

        Flight result = mapper.toDomain(req);

        assertThat(result.getTimeMultiplier()).isEqualTo(300);
    }

    @Test
    void testToDomainFallsBackToDefaultWhenNull() {
        when(props.getTimeMultiplier()).thenReturn(120);
        FlightRequest req = request("LAX", "JFK", null);
        Flight result = mapper.toDomain(req);


        assertThat(result.getTimeMultiplier()).isEqualTo(120);
    }


    @Test
    void testToResponseMapsAllFields() {
        Instant started = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant completed = Instant.now();

        Flight flight = Flight.builder()
                .id(42)
                .origin("LAX")
                .destination("JFK")
                .airline("Test Air")
                .flightNumber("TA001")
                .active(false)
                .phase(FlightPhase.COMPLETED)
                .startedAt(started)
                .completedAt(completed)
                .timeMultiplier(60)
                .build();

        FlightResponse result = mapper.toResponse(flight);

        assertThat(result.id()).isEqualTo(42);
        assertThat(result.origin()).isEqualTo("LAX");
        assertThat(result.destination()).isEqualTo("JFK");
        assertThat(result.airline()).isEqualTo("Test Air");
        assertThat(result.flightNumber()).isEqualTo("TA001");
        assertThat(result.active()).isFalse();
        assertThat(result.phase()).isEqualTo(FlightPhase.COMPLETED);
        assertThat(result.startedAt()).isEqualTo(started);
        assertThat(result.completedAt()).isEqualTo(completed);
        assertThat(result.timeMultiplier()).isEqualTo(60);
    }


    private FlightRequest request(String origin, String destination, Integer multiplier) {
        return new FlightRequest(origin, destination, "TA001", "Test Air", multiplier);
    }
}