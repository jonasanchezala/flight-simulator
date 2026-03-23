package com.flight.simulator.stream;

import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.exception.FlightNotFoundException;
import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import com.flight.simulator.repository.FlightMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightStreamRegistryTest {

    @Mock
    private FlightMetricsRepository flightMetricsRepository;
    @Mock
    private MetricMapper metricMapper;

    @InjectMocks
    private FlightStreamRegistry registry;

    private static final int FLIGHT_ID = 1;

    @Test
    void testRegisterEmitDeliversMetricsToSubscriber() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        registry.register(FLIGHT_ID);
        MetricResponse metric = sampleMetricResponse();

        StepVerifier.create(registry.streamFlight(FLIGHT_ID).take(1))
                .then(() -> registry.emit(FLIGHT_ID, metric))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("METRICS");
                    assertThat(event.data()).isEqualTo(metric);
                })
                .thenCancel()
                .verify();
    }

    @Test
    void testRegisterEmitSetsEventTypeToMetrics() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        registry.register(FLIGHT_ID);

        StepVerifier.create(registry.streamFlight(FLIGHT_ID).take(1))
                .then(() -> registry.emit(FLIGHT_ID, sampleMetricResponse()))
                .assertNext(event -> assertThat(event.event()).isEqualTo("METRICS"))
                .thenCancel()
                .verify();
    }

    @Test
    void testRegisterCompleteSendsCompletedEvent() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        registry.register(FLIGHT_ID);

        StepVerifier.create(registry.streamFlight(FLIGHT_ID).take(1))
                .then(() -> registry.complete(FLIGHT_ID))
                .assertNext(event -> assertThat(event.event()).isEqualTo("COMPLETED"))
                .verifyComplete();
    }

    @Test
    void testStreamFlightReplaysHistoryFirst() {
        FlightMetrics entity = sampleMetricsEntity();
        MetricResponse historic = sampleMetricResponse();

        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.just(entity));
        when(metricMapper.toResponse(entity)).thenReturn(historic);

        registry.register(FLIGHT_ID);

        StepVerifier.create(registry.streamFlight(FLIGHT_ID).take(2))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("METRICS");
                    assertThat(event.data()).isEqualTo(historic);
                })
                .then(() -> registry.complete(FLIGHT_ID))
                .assertNext(event -> assertThat(event.event()).isEqualTo("COMPLETED"))
                .verifyComplete();
    }

    @Test
    void testStreamFlightErrorsWhenNotRegistered() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(registry.streamFlight(FLIGHT_ID))
                .expectError(FlightNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("streamFlight() emits COMPLETED as final event when flight completes")
    void testStreamFlightEmitsCompletedEventAtEnd() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        registry.register(FLIGHT_ID);

        StepVerifier.create(registry.streamFlight(FLIGHT_ID))
                .then(() -> registry.complete(FLIGHT_ID))
                .assertNext(event -> assertThat(event.event()).isEqualTo("COMPLETED"))
                .verifyComplete();
    }

    @Test
    void testStreamFlightIncludesFlightComment() {
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(FLIGHT_ID))
                .thenReturn(Flux.empty());

        registry.register(FLIGHT_ID);

        StepVerifier.create(registry.streamFlight(FLIGHT_ID).take(1))
                .then(() -> registry.emit(FLIGHT_ID, sampleMetricResponse()))
                .assertNext(event ->
                        assertThat(event.comment()).contains(String.valueOf(FLIGHT_ID)))
                .thenCancel()
                .verify();
    }

    private MetricResponse sampleMetricResponse() {
        return MetricResponse.builder()
                .id(1)
                .flightId(FLIGHT_ID)
                .recordedAt(Instant.now())
                .phase(FlightPhase.CRUISE)
                .altitudeFeet(37000.0)
                .airspeedKnots(480.0)
                .headingDegrees(66.0)
                .latitude(37.5)
                .longitude(-95.0)
                .fuelPercentage(72.0)
                .outsideAirTempCelsius(-56.5)
                .etaMinutes(100.0)
                .build();
    }

    private FlightMetrics sampleMetricsEntity() {
        return FlightMetrics.builder()
                .id(1)
                .flightId(FLIGHT_ID)
                .recordedAt(Instant.now())
                .phase(FlightPhase.CRUISE)
                .altitudeFeet(37000.0)
                .airspeedKnots(480.0)
                .headingDegrees(66.0)
                .latitude(37.5)
                .longitude(-95.0)
                .fuelPercentage(72.0)
                .outsideAirTempCelsius(-56.5)
                .etaMinutes(100.0)
                .build();
    }
}
