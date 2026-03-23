package com.flight.simulator.service;

import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.dto.FlightStatusResponse;
import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.exception.FlightNotFoundException;
import com.flight.simulator.mapper.FlightMapper;
import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.repository.FlightMetricsRepository;
import com.flight.simulator.repository.FlightRepository;
import com.flight.simulator.stream.FlightStreamRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final FlightStreamRegistry flightStreamRegistry;
    private final FlightRepository flightRepository;
    private final FlightMetricsRepository flightMetricsRepository;
    private final FlightMapper flightMapper;
    private final MetricMapper metricMapper;

    public Mono<FlightResponse> startFlight(@Valid FlightRequest request) {
        return Mono.just(request)
                .map(flightMapper::toDomain)
                .flatMap(flightRepository::save)
                .doOnSuccess(flight -> flightStreamRegistry.register(flight.getId()))
                .map(flightMapper::toResponse)
                .doOnError(ex -> log.error("Failed to save flight for request {}", request.flightNumber(), ex));
    }

    public Flux<FlightResponse> listFlights() {
        return flightRepository
                .findAll()
                .map(flightMapper::toResponse);
    }

    public Mono<FlightStatusResponse> getFlightStatus(Integer flightId) {
        return flightRepository.findById(flightId)
                .switchIfEmpty(Mono.error(new FlightNotFoundException(flightId)))
                .flatMap(flight ->
                        flightMetricsRepository.findTopByFlightIdOrderByRecordedAtDesc(flightId)
                                .map(metricMapper::toResponse)
                                .map(latest -> FlightStatusResponse.builder()
                                        .flight(flightMapper.toResponse(flight))
                                        .latestMetric(latest)
                                        .build())
                                .defaultIfEmpty(FlightStatusResponse.builder()
                                        .flight(flightMapper.toResponse(flight))
                                        .build())
                );
    }

    public Flux<MetricResponse> getFlightHistory(Integer flightId) {
        return flightRepository.findById(flightId)
                .switchIfEmpty(Mono.error(new FlightNotFoundException(flightId)))
                .thenMany(Flux.defer(() ->
                        flightMetricsRepository.findByFlightIdOrderByRecordedAtAsc(flightId)
                                .map(metricMapper::toResponse)));
    }

    public Flux<ServerSentEvent<MetricResponse>> streamFlight(Integer id) {
        return flightStreamRegistry.streamFlight(id);
    }
}
