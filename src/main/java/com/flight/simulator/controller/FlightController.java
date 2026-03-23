package com.flight.simulator.controller;

import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.dto.FlightStatusResponse;
import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/flights")
@RequiredArgsConstructor
@Tag(name = "Flights", description = "Flight simulation management API")
public class FlightController {

    private final FlightService flightService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Start a new flight simulation",
            description = "Creates and starts a new flight simulation. " +
                    "Returns the newly created flight resource.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Flight started",
                            content = @Content(schema = @Schema(implementation = FlightResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error")
            }
    )
    public Mono<FlightResponse> startFlight(@Valid @RequestBody FlightRequest request) {
        return flightService.startFlight(request);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List all flights",
            description = "Returns all flights."
    )
    public Flux<FlightResponse> listFlights() {
        return flightService.listFlights();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get flight status",
            description = "Returns current flight info and the latest metrics snapshot.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Flight not found")
            }
    )
    public Mono<FlightStatusResponse> getFlightStatus(
            @Parameter(description = "Flight Id") @PathVariable int id) {
        return flightService.getFlightStatus(id);
    }

    @GetMapping(value = "/{id}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get flight metric history",
            description = "Returns the complete ordered history of metric snapshots for a flight.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Flight not found")
            }
    )
    public Flux<MetricResponse> getFlightHistory(
            @Parameter(description = "Flight Id") @PathVariable Integer id) {
        return flightService.getFlightHistory(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream live flight metrics via Server-Sent Events",
            description = """
                    Emits two event types:
                    - `METRICS`   — one per simulation tick, full metrics payload
                    - `COMPLETED` — terminal signal when the flight finishes
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "SSE stream opened"),
                    @ApiResponse(responseCode = "404", description = "Flight not found")
            }
    )
    public Flux<ServerSentEvent<MetricResponse>> streamFlightMetrics(@PathVariable Integer id) {
        return flightService.streamFlight(id);
    }
}
