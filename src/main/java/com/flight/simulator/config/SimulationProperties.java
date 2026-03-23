package com.flight.simulator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    @Min(1)
    @Max(3600)
    private int timeMultiplier = 60;

    @Min(1)
    @Max(60)
    private int tickIntervalSeconds = 5;

    private PhaseDurations phases = new PhaseDurations();

    @Getter
    @Setter
    public static class PhaseDurations {
        @Min(1)
        private int boarding = 30;
        @Min(1)
        private int taxiOut = 15;
        @Min(1)
        private int takeoffClimb = 25;
        @Min(1)
        private int cruise = 210;
        @Min(1)
        private int descent = 25;
        @Min(1)
        private int landing = 5;
        @Min(1)
        private int taxiIn = 10;
    }
}