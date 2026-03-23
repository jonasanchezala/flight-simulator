package com.flight.simulator.service;

import com.flight.simulator.config.SimulationProperties;
import com.flight.simulator.dto.PhaseDefinition;
import com.flight.simulator.model.FlightPhase;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.flight.simulator.model.FlightPhase.*;

@Service
public class PropertiesPhaseService implements PhaseService {

    private final List<PhaseDefinition> definitions;

    public PropertiesPhaseService(SimulationProperties props) {
        SimulationProperties.PhaseDurations phases = props.getPhases();
        this.definitions = List.of(
                new PhaseDefinition(BOARDING, phases.getBoarding()),
                new PhaseDefinition(TAXI_OUT, phases.getTaxiOut()),
                new PhaseDefinition(TAKEOFF_CLIMB, phases.getTakeoffClimb()),
                new PhaseDefinition(CRUISE, phases.getCruise()),
                new PhaseDefinition(DESCENT, phases.getDescent()),
                new PhaseDefinition(LANDING, phases.getLanding()),
                new PhaseDefinition(TAXI_IN, phases.getTaxiIn())
        );
    }

    @Override
    public FlightPhase resolve(double elapsedMinutes) {
        double cursor = 0;
        for (PhaseDefinition def : definitions) {
            cursor += def.durationMinutes();
            if (elapsedMinutes < cursor) return def.phase();
        }
        return COMPLETED;
    }

    @Override
    public int totalDurationMinutes() {
        return definitions.stream()
                .mapToInt(PhaseDefinition::durationMinutes)
                .sum();
    }

    @Override
    public int startMinuteOf(FlightPhase phase) {
        int total = 0;
        for (PhaseDefinition def : definitions) {
            if (def.phase() == phase) return total;
            total += def.durationMinutes();
        }
        throw new IllegalArgumentException("Unknown phase: " + phase);
    }

    @Override
    public int durationOf(FlightPhase phase) {
        return definitions.stream()
                .filter(phaseDefinition -> phaseDefinition.phase() == phase)
                .mapToInt(PhaseDefinition::durationMinutes)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Phase not found in resolver: " + phase));
    }
}