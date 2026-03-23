package com.flight.simulator.mapper;

import com.flight.simulator.dto.MetricResponse;
import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MetricMapperTest {

    private final MetricMapper metricMapper = new MetricMapper();

    @Test
    void testToResponseMapsAllFields() {
        Instant now = Instant.now();

        FlightMetrics metrics = FlightMetrics.builder()
                .id(1)
                .flightId(42)
                .recordedAt(now)
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

        MetricResponse result = metricMapper.toResponse(metrics);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.flightId()).isEqualTo(42);
        assertThat(result.recordedAt()).isEqualTo(now);
        assertThat(result.phase()).isEqualTo(FlightPhase.CRUISE);
        assertThat(result.altitudeFeet()).isEqualTo(37000.0);
        assertThat(result.airspeedKnots()).isEqualTo(480.0);
        assertThat(result.headingDegrees()).isEqualTo(66.0);
        assertThat(result.latitude()).isEqualTo(37.5);
        assertThat(result.longitude()).isEqualTo(-95.0);
        assertThat(result.fuelPercentage()).isEqualTo(72.0);
        assertThat(result.outsideAirTempCelsius()).isEqualTo(-56.5);
        assertThat(result.etaMinutes()).isEqualTo(100.0);
    }
}
