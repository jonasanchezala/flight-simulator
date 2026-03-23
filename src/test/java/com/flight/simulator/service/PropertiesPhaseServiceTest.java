package com.flight.simulator.service;

import com.flight.simulator.config.SimulationProperties;
import com.flight.simulator.model.FlightPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertiesPhaseServiceTest {

    private PropertiesPhaseService service;

    @BeforeEach
    void setUp() {
        service = new PropertiesPhaseService(new SimulationProperties());
    }

    @Test
    void testResolveBoardingAtZero() {
        assertThat(service.resolve(0)).isEqualTo(FlightPhase.BOARDING);
    }

    @Test
    void testResolveTaxiOutAtBoardingBoundary() {
        assertThat(service.resolve(30)).isEqualTo(FlightPhase.TAXI_OUT);
    }

    @Test
    void testResolveTakeoffClimbAtFortyFive() {
        assertThat(service.resolve(45)).isEqualTo(FlightPhase.TAKEOFF_CLIMB);
    }

    @Test
    void testResolveCruiseAtSeventy() {
        assertThat(service.resolve(70)).isEqualTo(FlightPhase.CRUISE);
    }

    @Test
    void testResolveDescentAtTwoEighty() {
        assertThat(service.resolve(280)).isEqualTo(FlightPhase.DESCENT);
    }

    @Test
    void testResolveLandingAtThreeOFive() {
        assertThat(service.resolve(305)).isEqualTo(FlightPhase.LANDING);
    }

    @Test
    void testResolveTaxiInAtThreeTen() {
        assertThat(service.resolve(310)).isEqualTo(FlightPhase.TAXI_IN);
    }

    @Test
    void testResolveCompletedAtTotalDuration() {
        assertThat(service.resolve(320)).isEqualTo(FlightPhase.COMPLETED);
    }


    @Test
    void testTotalDurationIsThreeTwenty() {
        assertThat(service.totalDurationMinutes()).isEqualTo(320);
    }
}
