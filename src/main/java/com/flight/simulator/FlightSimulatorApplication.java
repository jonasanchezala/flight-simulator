package com.flight.simulator;

import com.flight.simulator.config.SimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulationProperties.class)
public class FlightSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightSimulatorApplication.class, args);
    }
}
