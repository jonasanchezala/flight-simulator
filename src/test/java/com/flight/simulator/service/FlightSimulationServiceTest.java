package com.flight.simulator.service;

import com.flight.simulator.model.FlightMetrics;
import com.flight.simulator.model.FlightPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSimulationServiceTest {

    @Mock
    private PhaseService phaseService;

    @InjectMocks
    private FlightSimulationService service;

    private static final int FLIGHT_ID = 1;


    @Test
    void testComputePhaseMatchesResolvedPhase() {
        stubPhaseService(FlightPhase.CRUISE, 70, 210);

        FlightMetrics result = service.compute(FLIGHT_ID, 100);

        assertThat(result.getPhase()).isEqualTo(FlightPhase.CRUISE);
    }


    @Test
    void testComputeAltitudeIsZeroDuringBoarding() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getAltitudeFeet()).isEqualTo(0);
    }

    @Test
    void testComputeAltitudeIsMaxDuringCruise() {
        stubPhaseService(FlightPhase.CRUISE, 70, 210);

        FlightMetrics result = service.compute(FLIGHT_ID, 150);

        assertThat(result.getAltitudeFeet()).isEqualTo(37000.0);
    }

    @Test
    void testComputeAirspeedIsZeroDuringBoarding() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getAirspeedKnots()).isEqualTo(0);
    }

    @Test
    void testComputeAirspeedIsTaxiSpeedDuringTaxiOut() {
        stubPhaseService(FlightPhase.TAXI_OUT, 30, 15);

        FlightMetrics result = service.compute(FLIGHT_ID, 35);

        assertThat(result.getAirspeedKnots()).isEqualTo(15.0);
    }

    @Test
    void testComputeHeadingIsZeroDuringBoarding() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getHeadingDegrees()).isEqualTo(0);
    }

    @Test
    void testComputeHeadingIsApproximately66DuringCruise() {
        stubPhaseService(FlightPhase.CRUISE, 70, 210);

        FlightMetrics result = service.compute(FLIGHT_ID, 150);

        assertThat(result.getHeadingDegrees()).isBetween(65.0, 67.0);
    }

    @Test
    void testComputeFuelStartsAtHundredPercent() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getFuelPercentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Fuel decreases over time")
    void testComputeFuelDecreasesOverTime() {
        when(phaseService.resolve(50)).thenReturn(FlightPhase.TAXI_OUT);
        when(phaseService.resolve(150)).thenReturn(FlightPhase.CRUISE);
        stubCommonPhaseServiceCalls(0, 30);

        double fuel1 = service.compute(FLIGHT_ID, 50).getFuelPercentage();
        double fuel2 = service.compute(FLIGHT_ID, 150).getFuelPercentage();

        assertThat(fuel1).isGreaterThan(fuel2);
    }

    @Test
    void testComputeOatIsTwentyDegreesOnGround() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getOutsideAirTempCelsius()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("OAT is near tropopause temperature at cruise altitude")
    void testComputeOatIsNearTropopauseDuringCruise() {
        stubPhaseService(FlightPhase.CRUISE, 70, 210);

        FlightMetrics result = service.compute(FLIGHT_ID, 150);

        assertThat(result.getOutsideAirTempCelsius()).isBetween(-70.0, -40.0);
    }

    @Test
    void etaIsPositiveBeforeTakeoff() {
        stubPhaseService(FlightPhase.BOARDING, 0, 30);

        FlightMetrics result = service.compute(FLIGHT_ID, 0);

        assertThat(result.getEtaMinutes()).isGreaterThan(0);
    }

    @Test
    void testComputeEtaDecreasesDuringFlight() {
        when(phaseService.resolve(80)).thenReturn(FlightPhase.CRUISE);
        when(phaseService.resolve(180)).thenReturn(FlightPhase.CRUISE);
        stubCommonPhaseServiceCalls(70, 210);

        double eta1 = service.compute(FLIGHT_ID, 80).getEtaMinutes();
        double eta2 = service.compute(FLIGHT_ID, 180).getEtaMinutes();

        assertThat(eta1).isGreaterThan(eta2);
    }

    @Test
    void testComputeEtaIsZeroAfterLanding() {
        stubPhaseService(FlightPhase.TAXI_IN, 310, 10);

        FlightMetrics result = service.compute(FLIGHT_ID, 315);

        assertThat(result.getEtaMinutes()).isEqualTo(0);
    }

    private void stubPhaseService(
            FlightPhase phase,
            int phaseStart,
            int phaseDuration) {

        lenient().when(phaseService.resolve(anyDouble())).thenReturn(phase);
        lenient().when(phaseService.startMinuteOf(phase)).thenReturn(phaseStart);
        lenient().when(phaseService.durationOf(phase)).thenReturn(phaseDuration);
        stubCommonPhaseServiceCalls(
                310, phaseDuration
        );
    }

    private void stubCommonPhaseServiceCalls(
            int boardingStart,
            int boardingDuration) {
        lenient().when(phaseService.startMinuteOf(FlightPhase.BOARDING)).thenReturn(boardingStart);
        lenient().when(phaseService.durationOf(FlightPhase.BOARDING)).thenReturn(boardingDuration);
        lenient().when(phaseService.startMinuteOf(FlightPhase.TAKEOFF_CLIMB)).thenReturn(45);
        lenient().when(phaseService.startMinuteOf(FlightPhase.LANDING)).thenReturn(310);
        lenient().when(phaseService.durationOf(FlightPhase.LANDING)).thenReturn(5);
        lenient().when(phaseService.durationOf(FlightPhase.TAXI_IN)).thenReturn(310);
        lenient().when(phaseService.totalDurationMinutes()).thenReturn(320);
    }
}
