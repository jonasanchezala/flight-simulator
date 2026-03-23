package com.flight.simulator.repository;

import com.flight.simulator.model.Flight;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface FlightRepository extends ReactiveCrudRepository<Flight, Integer> {
    Flux<Flight> findByActive(boolean active);
}