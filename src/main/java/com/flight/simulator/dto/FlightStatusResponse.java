package com.flight.simulator.dto;

import lombok.Builder;

@Builder
public record FlightStatusResponse(FlightResponse flight,
                                   MetricResponse latestMetric) {
}