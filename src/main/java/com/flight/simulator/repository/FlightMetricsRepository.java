package com.flight.simulator.repository;

import com.flight.simulator.model.FlightMetrics;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlightMetricsRepository extends ReactiveCrudRepository<FlightMetrics, Integer> {

    Flux<FlightMetrics> findByFlightIdOrderByRecordedAtAsc(Integer flightId);

    Mono<FlightMetrics> findTopByFlightIdOrderByRecordedAtDesc(Integer flightId);
}

