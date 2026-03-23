package com.flight.simulator.stream;

import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.exception.FlightNotFoundException;
import com.flight.simulator.mapper.MetricMapper;
import com.flight.simulator.repository.FlightMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightStreamRegistry {

    private static final Duration SSE_RETRY = Duration.ofSeconds(3);

    private final FlightMetricsRepository flightMetricsRepository;
    private final MetricMapper metricMapper;

    private final Map<Integer, Sinks.Many<MetricResponse>> sinks = new ConcurrentHashMap<>();

    public void register(Integer flightId) {
        log.info("Flight started with id {}", flightId);
        sinks.put(flightId, Sinks.many().multicast().onBackpressureBuffer());
        log.debug("SSE sink registered for flight {}", flightId);
    }

    public void emit(Integer flightId, MetricResponse metrics) {
        Sinks.Many<MetricResponse> sink = sinks.get(flightId);
        if (sink == null) {
            log.warn("emit() called for unknown flight {}", flightId);
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(metrics);
        if (result.isFailure()) {
            log.warn("Failed to emit metrics for flight {}: {}", flightId, result);
        }
    }

    public void complete(Integer flightId) {
        Sinks.Many<MetricResponse> sink = sinks.remove(flightId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("SSE sink completed and removed for flight {}", flightId);
        }
    }

    public Flux<ServerSentEvent<MetricResponse>> streamFlight(Integer flightId) {
        Flux<MetricResponse> history = buildHistory(flightId);
        Flux<MetricResponse> live = buildLive(flightId);

        return Flux.concat(history, live)
                .map(metrics -> toSseEvent(flightId, metrics))
                .concatWith(completionEvent(flightId));
    }

    private Flux<MetricResponse> buildHistory(Integer flightId) {
        return flightMetricsRepository
                .findByFlightIdOrderByRecordedAtAsc(flightId)
                .map(metricMapper::toResponse);
    }

    private Flux<MetricResponse> buildLive(Integer flightId) {
        return Flux.defer(() -> {
            Sinks.Many<MetricResponse> sink = sinks.get(flightId);
            if (sink == null) {
                return Flux.error(new FlightNotFoundException(flightId));
            }
            return sink.asFlux();
        });
    }

    private ServerSentEvent<MetricResponse> toSseEvent(Integer flightId, MetricResponse metric) {
        return ServerSentEvent.<MetricResponse>builder()
                .id(UUID.randomUUID().toString())
                .event("METRICS")
                .retry(SSE_RETRY)
                .comment("flight:" + flightId)
                .data(metric)
                .build();
    }

    private Mono<ServerSentEvent<MetricResponse>> completionEvent(Integer flightId) {
        return Mono.just(
                ServerSentEvent.<MetricResponse>builder()
                        .event("COMPLETED")
                        .comment("Flight " + flightId + " simulation finished")
                        .build()
        );
    }
}