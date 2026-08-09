package com.sentinel.api.controller;

import com.sentinel.api.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SimulationController — REST API for triggering live demo simulations.
 *
 * Implements the Global Lock + Time-Boxed Burst pattern:
 *   - Only one simulation can run at a time (Redis lock)
 *   - Auto-stops after 2 minutes
 *   - If concurrent request arrives, returns 409 Conflict
 *
 * This follows the industry-standard "Shared Staging + Chaos Engineering" approach.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * POST /api/v1/system/simulate
     * Triggers a 2-minute simulation burst.
     * Returns 200 if started, 409 if one is already running.
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> triggerSimulation() {
        log.info("🚀 POST /system/simulate — Attempting to trigger simulation");

        boolean started = simulationService.triggerSimulation();

        if (started) {
            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Simulation started! Normal traffic is flowing."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "already_running",
                    "message", "A simulation is already running."
            ));
        }
    }

    /**
     * POST /api/v1/system/simulate/stop
     * Stops the running simulation.
     */
    @PostMapping("/simulate/stop")
    public ResponseEntity<Map<String, Object>> stopSimulation() {
        log.info("🛑 POST /system/simulate/stop — Attempting to stop simulation");

        boolean stopped = simulationService.stopSimulation();

        if (stopped) {
            return ResponseEntity.ok(Map.of(
                    "status", "stopped",
                    "message", "Simulation stopped."
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "not_running",
                    "message", "No simulation is currently running."
            ));
        }
    }

    /**
     * GET /api/v1/system/simulation-status
     * Check if a simulation is currently active.
     */
    @GetMapping("/simulation-status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        boolean active = simulationService.isSimulationActive();
        return ResponseEntity.ok(Map.of(
                "active", active,
                "message", active
                        ? "A simulation is currently running. Watch the dashboard for live updates."
                        : "No simulation running. Click 'Trigger Simulation' to start one."
        ));
    }
}
