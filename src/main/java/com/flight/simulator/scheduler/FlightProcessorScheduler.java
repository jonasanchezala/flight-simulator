package com.flight.simulator.scheduler;

import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.model.Flight;
import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import com.flight.simulator.repository.FlightMetricsRepository;
import com.flight.simulator.repository.FlightRepository;
import com.flight.simulator.service.FlightSimulationService;
import com.flight.simulator.service.PhaseService;
import com.flight.simulator.stream.FlightStreamRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightProcessorScheduler {

    private final FlightSimulationService flightSimulationService;
    private final PhaseService phaseService;
    private final FlightStreamRegistry streamRegistry;
    private final FlightRepository flightRepository;
    private final FlightMetricsRepository flightMetricsRepository;
    private final MetricMapper metricMapper;


    @Scheduled(fixedDelayString = "${simulation.tick-interval-seconds:5}000")
    public void tick() {
        flightRepository.findByActive(Boolean.TRUE)
                .flatMap(this::tickFlight)
                .subscribe(
                        v -> {
                        },
                        err -> log.error("Error during simulation tick", err)
                );
    }

    @Transactional
    private Mono<Void> tickFlight(Flight flight) {
        double simulatedMinutes = computeElapsedMinutes(flight);
        FlightPhase currentPhase = phaseService.resolve(simulatedMinutes);

        if (currentPhase == FlightPhase.COMPLETED) {
            return completeFlight(flight);
        }

        return Mono.just(flightSimulationService.compute(flight.getId(), simulatedMinutes))
                .doOnNext(metrics -> flight.setPhase(currentPhase))
                .doOnNext(metrics -> streamRegistry.emit(flight.getId(), metricMapper.toResponse(metrics)))
                .flatMap(metrics -> persistTick(flight, metrics, currentPhase));
    }

    private double computeElapsedMinutes(Flight flight) {
        double realSeconds = Duration.between(flight.getStartedAt(), Instant.now())
                .toMillis() / 1000.0;
        return realSeconds * flight.getTimeMultiplier() / 60.0;
    }


    private Mono<Void> completeFlight(Flight flight) {
        log.info("Flight {} completed", flight.getId());
        flight.setActive(Boolean.FALSE);
        flight.setPhase(FlightPhase.COMPLETED);
        flight.setCompletedAt(Instant.now());
        streamRegistry.complete(flight.getId());
        return flightRepository.save(flight).then();
    }

    private Mono<Void> persistTick(Flight flight, FlightMetrics metrics, FlightPhase phase) {
        return flightRepository.save(flight)
                .then(flightMetricsRepository.save(metrics))
                .then()
                .doOnSuccess(v -> log.debug("Tick flight {} -> phase={}, alt={} ft",
                        flight.getId(), phase, metrics.getAltitudeFeet()));
    }

}
