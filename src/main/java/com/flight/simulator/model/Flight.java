package com.flight.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("flights")
public class Flight {

    @Id
    private Integer id;
    @Builder.Default
    private boolean active = true;
    private String origin;
    private String destination;
    private String airline;
    private String flightNumber;
    private FlightPhase phase;
    private Instant startedAt;
    private Instant completedAt;
    private int timeMultiplier;
}
