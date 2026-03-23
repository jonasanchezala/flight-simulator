package com.flight.simulator.service;

import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.exception.FlightNotFoundException;
import com.flight.simulator.mapper.FlightMapper;
import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.model.Flight;
import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import com.flight.simulator.repository.FlightMetricsRepository;
import com.flight.simulator.repository.FlightRepository;
import com.flight.simulator.stream.FlightStreamRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock
    private FlightStreamRegistry flightStreamRegistry;
    @Mock
    private FlightRepository flightRepository;
    @Mock
    private FlightMetricsRepository flightMetricsRepository;
    @Mock
    private FlightMapper flightMapper;
    @Mock
    private MetricMapper metricMapper;

    @InjectMocks
    private FlightService service;

    @Test
    void testStartFlightMapsAndSavesAndReturnsResponse() {
        FlightRequest request = validRequest();
        Flight domain = activeFlight(1);
        FlightResponse response = sampleFlightResponse(1);

        when(flightMapper.toDomain(request)).thenReturn(domain);
        when(flightRepository.save(domain)).thenReturn(Mono.just(domain));
        when(flightMapper.toResponse(domain)).thenReturn(response);

        StepVerifier.create(service.startFlight(request))
                .assertNext(flightResponse -> assertThat(flightResponse.id()).isEqualTo(1))
                .verifyComplete();

        verify(flightMapper).toDomain(request);
        verify(flightRepository).save(domain);
        verify(flightMapper).toResponse(domain);
    }

    @Test
    void testStartFlightRegistersSinkAfterSave() {
        FlightRequest request = validRequest();
        Flight domain = activeFlight(1);

        when(flightMapper.toDomain(request)).thenReturn(domain);
        when(flightRepository.save(domain)).thenReturn(Mono.just(domain));
        when(flightMapper.toResponse(domain)).thenReturn(sampleFlightResponse(1));

        StepVerifier.create(service.startFlight(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(flightStreamRegistry).register(1);
    }

    @Test
    void testStartFlightDoesNotRegisterSinkWhenSaveFails() {
        FlightRequest request = validRequest();
        Flight domain = activeFlight(1);

        when(flightMapper.toDomain(request)).thenReturn(domain);
        when(flightRepository.save(domain))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(service.startFlight(request))
                .expectError(RuntimeException.class)
                .verify();

        verify(flightStreamRegistry, never()).register(any());
    }

    @Test
    void testStartFlightPropagatesRepositoryError() {
        FlightRequest request = validRequest();
        Flight domain = activeFlight(1);

        when(flightMapper.toDomain(request)).thenReturn(domain);
        when(flightRepository.save(domain))
                .thenReturn(Mono.error(new RuntimeException("DB unavailable")));

        StepVerifier.create(service.startFlight(request))
                .expectError(RuntimeException.class)
                .verify();
    }


    @Test
    void testListFlightsReturnsAllFlightsMapped() {
        Flight f1 = activeFlight(1);
        Flight f2 = activeFlight(2);

        when(flightRepository.findAll()).thenReturn(Flux.just(f1, f2));
        when(flightMapper.toResponse(f1)).thenReturn(sampleFlightResponse(1));
        when(flightMapper.toResponse(f2)).thenReturn(sampleFlightResponse(2));

        StepVerifier.create(service.listFlights())
                .assertNext(flightResponse -> assertThat(flightResponse.id()).isEqualTo(1))
                .assertNext(flightResponse -> assertThat(flightResponse.id()).isEqualTo(2))
                .verifyComplete();

        verify(flightMapper, times(2)).toResponse(any(Flight.class));
    }

    @Test
    void testListFlightsReturnsEmptyFlux() {
        when(flightRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(service.listFlights())
                .verifyComplete();

        verifyNoInteractions(flightMapper);
    }

    @Test
    void testGetFlightStatusReturnsFlightWithLatestMetrics() {
        Flight flight = activeFlight(1);
        FlightMetrics metrics = sampleMetrics(1);
        FlightResponse flightR = sampleFlightResponse(1);
        MetricResponse metricR = sampleMetricResponse(1);

        when(flightRepository.findById(1)).thenReturn(Mono.just(flight));
        when(flightMetricsRepository.findTopByFlightIdOrderByRecordedAtDesc(1))
                .thenReturn(Mono.just(metrics));
        when(flightMapper.toResponse(flight)).thenReturn(flightR);
        when(metricMapper.toResponse(metrics)).thenReturn(metricR);

        StepVerifier.create(service.getFlightStatus(1))
                .assertNext(response -> {
                    assertThat(response.flight().id()).isEqualTo(1);
                    assertThat(response.latestMetric()).isNotNull();
                    assertThat(response.latestMetric().altitudeFeet()).isEqualTo(37000.0);
                })
                .verifyComplete();
    }

    @Test
    void testGetFlightStatusReturnsFlightWithNullMetricsWhenNoneExist() {
        Flight flight = activeFlight(1);
        FlightResponse flightR = sampleFlightResponse(1);

        when(flightRepository.findById(1)).thenReturn(Mono.just(flight));
        when(flightMetricsRepository.findTopByFlightIdOrderByRecordedAtDesc(1))
                .thenReturn(Mono.empty());
        when(flightMapper.toResponse(flight)).thenReturn(flightR);

        StepVerifier.create(service.getFlightStatus(1))
                .assertNext(response -> {
                    assertThat(response.flight().id()).isEqualTo(1);
                    assertThat(response.latestMetric()).isNull();
                })
                .verifyComplete();

        verifyNoInteractions(metricMapper);
    }

    @Test
    void testGetFlightStatusThrowsFlightNotFoundForUnknownId() {
        when(flightRepository.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(service.getFlightStatus(99))
                .expectError(FlightNotFoundException.class)
                .verify();

        verifyNoInteractions(flightMetricsRepository, flightMapper, metricMapper);
    }


    @Test
    void testGetFlightHistoryReturnsOrderedHistory() {
        Flight flight = activeFlight(1);
        FlightMetrics m1 = sampleMetrics(1);
        FlightMetrics m2 = sampleMetrics(1);
        MetricResponse r1 = sampleMetricResponse(1);
        MetricResponse r2 = sampleMetricResponse(1);

        when(flightRepository.findById(1)).thenReturn(Mono.just(flight));
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(1))
                .thenReturn(Flux.just(m1, m2));
        when(metricMapper.toResponse(m1)).thenReturn(r1);
        when(metricMapper.toResponse(m2)).thenReturn(r2);

        StepVerifier.create(service.getFlightHistory(1))
                .expectNextCount(2)
                .verifyComplete();

        verify(metricMapper, times(2)).toResponse(any(FlightMetrics.class));
    }

    @Test
    void testGetFlightHistoryReturnsEmptyWhenNoMetrics() {
        when(flightRepository.findById(1)).thenReturn(Mono.just(activeFlight(1)));
        when(flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(1))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.getFlightHistory(1))
                .verifyComplete();

        verifyNoInteractions(metricMapper);
    }

    @Test
    void testGetFlightHistoryThrowsFlightNotFoundForUnknownId() {
        when(flightRepository.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(service.getFlightHistory(99))
                .expectError(FlightNotFoundException.class)
                .verify();

        verifyNoInteractions(flightMetricsRepository, metricMapper);
    }


    @Test
    @DisplayName("Delegates to FlightStreamRegistry")
    void testStreamFlightDelegatesToStreamRegistry() {
        ServerSentEvent<MetricResponse> event = ServerSentEvent.<MetricResponse>builder()
                .event("METRICS")
                .id("70.0")
                .data(sampleMetricResponse(1))
                .build();

        when(flightStreamRegistry.streamFlight(1)).thenReturn(Flux.just(event));

        StepVerifier.create(service.streamFlight(1))
                .assertNext(serverSentEvent -> {
                    assertThat(serverSentEvent.event()).isEqualTo("METRICS");
                    assertThat(serverSentEvent.id()).isEqualTo("70.0");
                })
                .verifyComplete();

        verify(flightStreamRegistry).streamFlight(1);
    }

    @Test
    void testStreamFlightPropagatesNotFoundFromRegistry() {
        when(flightStreamRegistry.streamFlight(99))
                .thenReturn(Flux.error(new FlightNotFoundException(99)));

        StepVerifier.create(service.streamFlight(99))
                .expectError(FlightNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Returns empty flux when stream completes")
    void testStreamFlightReturnsEmptyWhenStreamCompletes() {
        when(flightStreamRegistry.streamFlight(1)).thenReturn(Flux.empty());

        StepVerifier.create(service.streamFlight(1))
                .verifyComplete();
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private FlightRequest validRequest() {
        return new FlightRequest("LAX", "JFK", "Test Air", "TA001", 60);
    }

    private Flight activeFlight(int id) {
        return Flight.builder()
                .id(id)
                .origin("LAX")
                .destination("JFK")
                .airline("Test Air")
                .flightNumber("TA001")
                .active(true)
                .phase(FlightPhase.BOARDING)
                .startedAt(Instant.now())
                .timeMultiplier(60)
                .build();
    }

    private FlightResponse sampleFlightResponse(int id) {
        return FlightResponse.builder()
                .id(id)
                .origin("LAX")
                .destination("JFK")
                .airline("Test Air")
                .flightNumber("TA001")
                .active(true)
                .phase(FlightPhase.BOARDING)
                .startedAt(Instant.now())
                .timeMultiplier(60)
                .build();
    }

    private FlightMetrics sampleMetrics(int flightId) {
        return FlightMetrics.builder()
                .id(1)
                .flightId(flightId)
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

    private MetricResponse sampleMetricResponse(int flightId) {
        return MetricResponse.builder()
                .id(1)
                .flightId(flightId)
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
