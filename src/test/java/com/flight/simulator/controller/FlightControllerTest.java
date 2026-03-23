package com.flight.simulator.controller;

import com.flight.simulator.dto.FlightRequest;
import com.flight.simulator.dto.FlightResponse;
import com.flight.simulator.dto.FlightStatusResponse;
import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.exception.FlightNotFoundException;
import com.flight.simulator.service.FlightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(FlightController.class)
class FlightControllerTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private FlightService flightService;


    @Test
    void testStartFlightReturns201OnSuccess() {
        when(flightService.startFlight(any(FlightRequest.class)))
                .thenReturn(Mono.just(sampleFlightResponse(1)));

        client.post().uri("/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FlightResponse.class)
                .value(resp -> {
                    assertThat(resp.id()).isEqualTo(1);
                    assertThat(resp.origin()).isEqualTo("LAX");
                    assertThat(resp.destination()).isEqualTo("JFK");
                });
    }

    @Test
    void testStartFlightPropagatesServiceError() {
        when(flightService.startFlight(any()))
                .thenReturn(Mono.error(new RuntimeException("DB down")));

        client.post().uri("/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void testListFlightsReturns200WithFlights() {
        when(flightService.listFlights())
                .thenReturn(Flux.just(
                        sampleFlightResponse(1),
                        sampleFlightResponse(2)
                ));

        client.get().uri("/flights")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(FlightResponse.class)
                .hasSize(2)
                .value(list -> {
                    assertThat(list.get(0).id()).isEqualTo(1);
                    assertThat(list.get(1).id()).isEqualTo(2);
                });
    }

    @Test
    void testListFlightsReturns200WithEmptyList() {
        when(flightService.listFlights()).thenReturn(Flux.empty());

        client.get().uri("/flights")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FlightResponse.class)
                .hasSize(0);
    }

    @Test
    void testGetFlightStatusReturns200WithStatus() {
        when(flightService.getFlightStatus(1))
                .thenReturn(Mono.just(sampleFlightStatusResponse(1)));

        client.get().uri("/flights/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(FlightStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.flight().id()).isEqualTo(1);
                    assertThat(resp.latestMetric()).isNotNull();
                });
    }

    @Test
    void testGetFlightStatusReturns404WhenNotFound() {
        when(flightService.getFlightStatus(99))
                .thenReturn(Mono.error(new FlightNotFoundException(99)));

        client.get().uri("/flights/99")
                .exchange()
                .expectStatus().isNotFound();
    }


    @Test
    void testGetFlightHistoryReturns200WithHistory() {
        when(flightService.getFlightHistory(1))
                .thenReturn(Flux.just(
                        sampleMetricResponse(1),
                        sampleMetricResponse(1),
                        sampleMetricResponse(1)
                ));

        client.get().uri("/flights/1/history")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(MetricResponse.class)
                .hasSize(3)
                .value(list -> {
                    assertThat(list.get(0).flightId()).isEqualTo(1);
                    assertThat(list.get(1).flightId()).isEqualTo(1);
                    assertThat(list.get(2).flightId()).isEqualTo(1);
                });
    }

    @Test
    void testGetFlightHistoryReturns200WithEmptyHistory() {
        when(flightService.getFlightHistory(1)).thenReturn(Flux.empty());

        client.get().uri("/flights/1/history")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(MetricResponse.class)
                .hasSize(0);
    }

    @Test
    void testGetFlightHistoryReturns404WhenNotFound() {
        when(flightService.getFlightHistory(99))
                .thenReturn(Flux.error(new FlightNotFoundException(99)));

        client.get().uri("/flights/99/history")
                .exchange()
                .expectStatus().isNotFound();
    }


    @Test
    void testStreamFlightReturns200WithEventStreamContentType() {
        when(flightService.streamFlight(1))
                .thenReturn(Flux.just(
                        ServerSentEvent.<MetricResponse>builder()
                                .event("METRICS")
                                .id("30.0")
                                .data(sampleMetricResponse(1))
                                .build()
                ));

        client.get().uri("/flights/1/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void testStreamFlightEmitsMetricsEvents() {
        MetricResponse metric = sampleMetricResponse(1);
        when(flightService.streamFlight(1))
                .thenReturn(Flux.just(
                        ServerSentEvent.<MetricResponse>builder()
                                .event("METRICS")
                                .id("70.0")
                                .data(metric)
                                .build()
                ));

        client.get().uri("/flights/1/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(MetricResponse.class)
                .hasSize(1);
    }

    @Test
    void testStreamFlightReturns404WhenNotFound() {
        when(flightService.streamFlight(99))
                .thenReturn(Flux.error(new FlightNotFoundException(99)));

        client.get().uri("/flights/99/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }

    private FlightRequest validRequest() {
        return new FlightRequest("LAX", "JFK", "Test Air", "TA001", 60);

    }

    private FlightResponse sampleFlightResponse(int id) {
        return FlightResponse.builder()
                .id(id)
                .origin("LAX")
                .destination("JFK")
                .airline("Test Air")
                .flightNumber("TA001")
                .active(true)
                .startedAt(Instant.now())
                .timeMultiplier(60)
                .build();
    }

    private FlightStatusResponse sampleFlightStatusResponse(int id) {
        return FlightStatusResponse.builder()
                .flight(sampleFlightResponse(id))
                .latestMetric(sampleMetricResponse(id))
                .build();
    }

    private MetricResponse sampleMetricResponse(int flightId) {
        return MetricResponse.builder()
                .flightId(flightId)
                .recordedAt(Instant.now())
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