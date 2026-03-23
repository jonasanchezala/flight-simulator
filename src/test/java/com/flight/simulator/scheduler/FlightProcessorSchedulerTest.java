package com.flight.simulator.scheduler;

import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.model.Flight;
import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import com.flight.simulator.repository.FlightMetricsRepository;
import com.flight.simulator.repository.FlightRepository;
import com.flight.simulator.service.FlightSimulationService;
import com.flight.simulator.service.PhaseService;
import com.flight.simulator.stream.FlightStreamRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightProcessorSchedulerTest {

    @Mock
    private FlightSimulationService flightSimulationService;
    @Mock
    private PhaseService phaseService;
    @Mock
    private FlightStreamRegistry streamRegistry;
    @Mock
    private FlightRepository flightRepository;
    @Mock
    private FlightMetricsRepository flightMetricsRepository;
    @Mock
    private MetricMapper metricMapper;

    @InjectMocks
    private FlightProcessorScheduler flightProcessorScheduler;

    @Test
    void testTickProcessesAllActiveFlights() {
        Flight f1 = activeFlight(1, 60);
        Flight f2 = activeFlight(2, 60);
        FlightMetrics metrics = sampleMetrics();
        MetricResponse metricResponse = sampleMetricResponse();

        when(flightRepository.findByActive(Boolean.TRUE))
                .thenReturn(Flux.just(f1, f2));
        when(phaseService.resolve(anyDouble()))
                .thenReturn(FlightPhase.CRUISE);
        when(flightSimulationService.compute(anyInt(), anyDouble()))
                .thenReturn(metrics);
        when(metricMapper.toResponse(metrics))
                .thenReturn(metricResponse);
        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightMetricsRepository.save(any(FlightMetrics.class)))
                .thenReturn(Mono.just(metrics));

        flightProcessorScheduler.tick();

        verify(flightRepository).findByActive(Boolean.TRUE);
        verify(flightSimulationService, times(2)).compute(anyInt(), anyDouble());
    }

    @Test
    void testTickDoesNothingWhenNoActiveFlights() {
        when(flightRepository.findByActive(Boolean.TRUE))
                .thenReturn(Flux.empty());

        flightProcessorScheduler.tick();

        verify(flightSimulationService, never()).compute(anyInt(), anyDouble());
        verifyNoInteractions(phaseService, streamRegistry, flightMetricsRepository);
    }

    @Test
    @DisplayName("Updates flight phase before persisting")
    void testTickUpdatesFlightPhase() {
        Flight flight = activeFlight(1, 60);
        FlightMetrics metrics = sampleMetrics();

        when(phaseService.resolve(anyDouble())).thenReturn(FlightPhase.CRUISE);
        when(flightSimulationService.compute(anyInt(), anyDouble())).thenReturn(metrics);
        when(metricMapper.toResponse(any())).thenReturn(sampleMetricResponse());
        when(flightRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightMetricsRepository.save(any())).thenReturn(Mono.just(metrics));
        when(flightRepository.findByActive(Boolean.TRUE)).thenReturn(Flux.just(flight));

        flightProcessorScheduler.tick();

        verify(phaseService, timeout(1000)).resolve(anyDouble());
        assertThat(flight.getPhase()).isEqualTo(FlightPhase.CRUISE);
    }

    @Test
    void testTickEmitsToStreamRegistry() {
        Flight flight = activeFlight(1, 60);
        FlightMetrics metrics = sampleMetrics();
        MetricResponse metricResponse = sampleMetricResponse();

        when(phaseService.resolve(anyDouble())).thenReturn(FlightPhase.CRUISE);
        when(flightSimulationService.compute(anyInt(), anyDouble())).thenReturn(metrics);
        when(metricMapper.toResponse(metrics)).thenReturn(metricResponse);
        when(flightRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightMetricsRepository.save(any())).thenReturn(Mono.just(metrics));
        when(flightRepository.findByActive(Boolean.TRUE)).thenReturn(Flux.just(flight));

        flightProcessorScheduler.tick();

        verify(streamRegistry, timeout(1000)).emit(eq(1), eq(metricResponse));
    }

    @Test
    void testTickSaveFlightBeforeMetrics() {
        Flight flight = activeFlight(1, 60);
        FlightMetrics metrics = sampleMetrics();

        when(phaseService.resolve(anyDouble())).thenReturn(FlightPhase.BOARDING);
        when(flightSimulationService.compute(anyInt(), anyDouble())).thenReturn(metrics);
        when(metricMapper.toResponse(any())).thenReturn(sampleMetricResponse());
        when(flightRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightMetricsRepository.save(any())).thenReturn(Mono.just(metrics));
        when(flightRepository.findByActive(Boolean.TRUE)).thenReturn(Flux.just(flight));

        flightProcessorScheduler.tick();

        var order = inOrder(flightRepository, flightMetricsRepository);
        order.verify(flightRepository, timeout(1000)).save(flight);
        order.verify(flightMetricsRepository, timeout(1000)).save(metrics);
    }

    @Test
    void testTickSetsFlightInactiveOnCompletion() {
        Flight flight = activeFlight(1, 60);

        when(phaseService.resolve(anyDouble())).thenReturn(FlightPhase.COMPLETED);
        when(flightRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightRepository.findByActive(Boolean.TRUE)).thenReturn(Flux.just(flight));

        flightProcessorScheduler.tick();

        verify(flightRepository, timeout(1000)).save(flight);
        assertThat(flight.isActive()).isFalse();
        assertThat(flight.getPhase()).isEqualTo(FlightPhase.COMPLETED);
        assertThat(flight.getCompletedAt()).isNotNull();
    }

    @Test
    void testTickCompletesStreamOnCompletion() {
        Flight flight = activeFlight(1, 60);

        when(phaseService.resolve(anyDouble())).thenReturn(FlightPhase.COMPLETED);
        when(flightRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(flightRepository.findByActive(Boolean.TRUE)).thenReturn(Flux.just(flight));

        flightProcessorScheduler.tick();

        verify(streamRegistry, timeout(1000)).complete(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Flight activeFlight(int id, int timeMultiplier) {
        Flight flight = Flight.builder()
                .id(id)
                .origin("LAX")
                .destination("JFK")
                .airline("Test Air")
                .flightNumber("TA00" + id)
                .active(true)
                .phase(FlightPhase.BOARDING)
                .startedAt(Instant.now())
                .timeMultiplier(timeMultiplier)
                .build();
        return flight;
    }

    private FlightMetrics sampleMetrics() {
        return FlightMetrics.builder()
                .id(1)
                .flightId(1)
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

    private MetricResponse sampleMetricResponse() {
        return MetricResponse.builder()
                .id(1)
                .flightId(1)
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
