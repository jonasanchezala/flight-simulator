# ✈ Flight Status Simulator

A fully reactive backend service built with **Spring Boot 3** + **Spring WebFlux** that simulates a
commercial flight from **LAX → JFK**, exposing real-time metrics via a REST API and a
Server-Sent Events (SSE) stream.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Tech Stack](#tech-stack)
3. [Package Structure](#package-structure)
4. [Prerequisites](#prerequisites)
5. [Setup & Run](#setup--run)
6. [Postman Collection](#option-c--postman-collection)
7. [API Reference](#api-reference)
8. [Simulation Design](#simulation-design)
9. [Configuration](#configuration)
10. [Running Tests](#running-tests)
11. [Design Decisions & Assumptions](#design-decisions--assumptions)

---

## Architecture Overview

```
HTTP Client
    │
    ▼
FlightController
    │
    ├── FlightService              ← CRUD + SSE delegation
    │       ├── FlightMapper       ← request → domain → response
    │       ├── MetricMapper       ← entity → response
    │       └── FlightStreamRegistry ← SSE stream per flight
    │
    ├── FlightProcessorScheduler   ← Flux.interval tick loop
    │       ├── PhaseService       ← resolves current phase from elapsed time
    │       ├── FlightSimulationService ← computes all metrics (pure math)
    │       └── FlightStreamRegistry ← emits/completes SSE sinks
    │
    ├── FlightRepository           ← R2DBC reactive (PostgreSQL)
    └── FlightMetricsRepository
```

**Every tick** (configurable via `simulation.tick-interval-seconds`, default 5 real seconds):

1. `Flux.interval` fires — fetches all active flights from PostgreSQL
2. For each flight, compute `simulatedElapsedMinutes = realSeconds × timeMultiplier / 60`
3. `PhaseService.resolve()` determines the current `FlightPhase`
4. `FlightSimulationService.compute()` produces a full `FlightMetrics` snapshot
5. Flight phase is updated and both flight + metrics are persisted to PostgreSQL
6. Metrics are emitted to all SSE subscribers via `FlightStreamRegistry`
7. When phase reaches `COMPLETED`, the flight is marked inactive and the SSE sink is closed

---

## Tech Stack

| Layer           | Technology                              |
|-----------------|-----------------------------------------|
| Web / Async     | Spring WebFlux (Project Reactor)        |
| Persistence     | Spring Data R2DBC + PostgreSQL 16       |
| Build           | Gradle 8.6 (Groovy DSL)                 |
| Java            | 21                                      |
| API Docs        | SpringDoc OpenAPI 2.x / Swagger UI      |
| Containerisation| Docker + Docker Compose                 |
| Testing         | JUnit 5, Mockito, StepVerifier, WebTestClient |

---

## Package Structure

```
com.flight.simulator/
├── config/
│   ├── SimulationProperties.java     @ConfigurationProperties — validated phase durations
│   ├── WebFluxConfig.java            Jackson + OpenAPI beans
│   └── FlightIdGenerationCallback.java  BeforeSaveCallback — UUID on INSERT
│
├── controller/
│   ├── FlightController.java         REST + SSE endpoints
│   └── GlobalExceptionHandler.java   ApiException hierarchy → HTTP error shapes
│
├── dto/
│   ├── FlightRequest.java            record — inbound request
│   ├── FlightResponse.java           response DTO
│   ├── FlightStatusResponse.java     flight + latestMetric wrapper
│   ├── MetricResponse.java           metrics snapshot response
│   └── PhaseDefinition.java          record — phase + duration pair
│
├── exception/
│   ├── ApiException.java             abstract base — getHttpStatus() + getErrorCode()
│   └── FlightNotFoundException.java  404 NOT_FOUND
│
├── mapper/
│   ├── FlightMapper.java             FlightRequest ↔ Flight ↔ FlightResponse
│   └── MetricMapper.java             FlightMetrics → MetricResponse
│
├── model/
│   ├── Flight.java                   R2DBC entity — boolean active, FlightPhase enum
│   ├── FlightMetrics.java            R2DBC entity — BIGSERIAL id, @ReadOnlyProperty
│   └── FlightPhase.java              enum — pure type, no transition logic
│
├── repository/
│   ├── FlightRepository.java         findByActive(Boolean)
│   └── FlightMetricsRepository.java  history + top-by-flight queries
│
├── scheduler/
│   └── FlightProcessorScheduler.java Flux.interval tick loop, @Transactional tickFlight
│
├── service/
│   ├── FlightService.java            startFlight, listFlights, getFlightStatus, getFlightHistory
│   ├── FlightSimulationService.java  pure metric computation — altitude, speed, position…
│   ├── PhaseService.java             interface — resolve / startMinuteOf / durationOf
│   └── PropertiesPhaseService.java   PhaseService driven by SimulationProperties
│
└── stream/
    └── FlightStreamRegistry.java     SSE sink lifecycle — register / emit / complete / streamFlight
```

---

## Prerequisites

| Tool           | Version         |
|----------------|-----------------|
| Java           | 21+             |
| Gradle         | 8.6+ (or `./gradlew`) |
| Docker         | 24+ (optional)  |
| Docker Compose | v2 (optional)   |

---

## Setup & Run

### Docker Compose 

Starts **PostgreSQL 16** and the simulator in one command:

```bash
git clone <your-repo-url>
cd flight-simulator

docker compose up --build
```

| URL | Description |
|-----|-------------|
| http://localhost:8080 | API base URL |
| http://localhost:8080/swagger-ui.html | Interactive Swagger UI |
| http://localhost:8080/api-docs | Raw OpenAPI JSON spec |

```bash
docker compose down
```
---

### Postman Collection

A ready-made Postman collection is included at the root of the repository:

```
flight-simulator.postman_collection.json
```

**Import steps:**

1. Open Postman
2. Click **Import** (top left)
3. Select `flight-simulator.postman_collection.json`
4. The collection appears under **Collections** with all requests pre-configured

**Collection variables** (automatically managed):

| Variable | Default                 | Description                                          |
|----------|-------------------------|------------------------------------------------------|
| `baseUrl` | `http://localhost:8080` | Change this if running on a different host or port   |
| `flightId` | _manually set_          | Populated manually when **Start Flight** is executed |

**Recommended run order:**

```
1. Start Flight                          → creates a flight, saves flightId
2. List All Flights                      → confirms flight appears in the list
3. Get Flight Status                     → shows current phase + latest metrics
   (wait 5–10 seconds for ticks to fire)
4. Get Flight History                    → shows all recorded metric snapshots
5. Stream Flight Metrics (SSE)           → opens live event stream in Postman
```

**Included requests (11 total):**

| Request | Method | Expected Status |
|---------|--------|-----------------|
| Start Flight | `POST /flights` | 201 |
| Start Flight — Same Origin & Destination | `POST /flights` | 422 |
| Start Flight — Blank Origin | `POST /flights` | 400 |
| Start Flight — Invalid Time Multiplier | `POST /flights` | 422 |
| List All Flights | `GET /flights` | 200 |
| Get Flight Status | `GET /flights/{{flightId}}` | 200 |
| Get Flight Status — Not Found | `GET /flights/does-not-exist` | 404 |
| Get Flight History | `GET /flights/{{flightId}}/history` | 200 |
| Get Flight History — Not Found | `GET /flights/does-not-exist/history` | 404 |
| Stream Flight Metrics (SSE) | `GET /flights/{{flightId}}/stream` | 200 |
| Stream — Reconnect with Last-Event-ID | `GET /flights/{{flightId}}/stream` | 200 |


> **Note on SSE in Postman:** Postman displays the raw event stream in the response body.
> For a cleaner live stream experience use curl:
> ```bash
> curl -N http://localhost:8080/flights/$FLIGHT_ID/stream
> ```

---

## API Reference

### Base URL

```
http://localhost:8080
```

### Interactive Docs

```
http://localhost:8080/swagger-ui.html    ← interactive UI
http://localhost:8080/api-docs           ← raw OpenAPI JSON
```

---

### `POST /flights` — Start a new flight simulation

**Request body:**

```json
{
  "origin": "LAX",
  "destination": "JFK",
  "airline": "Simulated Airlines",
  "flightNumber": "SA001",
  "timeMultiplier": 60
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `origin` | string | ✅ | — | IATA code (3–4 chars) |
| `destination` | string | ✅ | — | IATA code (3–4 chars), must differ from origin |
| `airline` | string | ❌ | `"Simulated Airlines"` | Airline name |
| `flightNumber` | string | ❌ | `"SA001"` | Must be unique among active flights |
| `timeMultiplier` | int | ❌ | `60` | Simulated minutes per real second (must be > 0) |

**Response `201 Created`:**

```json
{
  "id": 1,
  "origin": "LAX",
  "destination": "JFK",
  "airline": "Simulated Airlines",
  "flightNumber": "SA001",
  "active": true,
  "phase": "BOARDING",
  "startedAt": "2024-03-22T10:00:00Z",
  "completedAt": null,
  "timeMultiplier": 60
}
```

**Error responses:**

| Status | Code | Reason |
|--------|------|--------|
| 400 | `VALIDATION_ERROR` | Blank origin/destination or missing required fields |
| 422 | `INVALID_FLIGHT` | Same origin and destination, negative multiplier, or duplicate active flight number |

```bash
curl -s -X POST http://localhost:8080/flights \
  -H "Content-Type: application/json" \
  -d '{
    "origin": "LAX",
    "destination": "JFK",
    "airline": "Simulated Airlines",
    "flightNumber": "SA001",
    "timeMultiplier": 120
  }' | jq .
```

---

### `GET /flights` — List all flights

Returns all flights (active and completed).

```bash
curl -s http://localhost:8080/flights | jq .
```

**Response `200 OK`:** Array of flight objects.

---

### `GET /flights/{id}` — Current flight status

Returns the flight and its most recent metrics snapshot.

```bash
FLIGHT_ID=1
curl -s http://localhost:8080/flights/$FLIGHT_ID | jq .
```

**Response `200 OK`:**

```json
{
  "flight": {
    "id": 1,
    "origin": "LAX",
    "destination": "JFK",
    "active": true,
    "phase": "CRUISE",
    "startedAt": "2024-03-22T10:00:00Z",
    "timeMultiplier": 120
  },
  "latestMetric": {
    "flightId": 1,
    "recordedAt": "2024-03-22T10:02:30Z",
    "phase": "CRUISE",
    "altitudeFeet": 37000.0,
    "airspeedKnots": 480.0,
    "headingDegrees": 66.09,
    "latitude": 37.85,
    "longitude": -98.12,
    "fuelPercentage": 72.4,
    "outsideAirTempCelsius": -56.5,
    "etaMinutes": 98.0
  }
}
```

**`404`** if the flight ID does not exist.

---

### `GET /flights/{id}/history` — Full metric history

Returns all metric snapshots for a flight in ascending chronological order.

```bash
curl -s http://localhost:8080/flights/$FLIGHT_ID/history | jq .
```

**`404`** if the flight ID does not exist.

---

### `GET /flights/{id}/stream` — Live SSE stream

Server-Sent Events endpoint. Opens a persistent connection and emits events until
the flight completes.

**Event types:**

| Event | Description |
|-------|-------------|
| `METRICS` | One per tick — full metrics JSON payload |
| `COMPLETED` | Terminal — flight simulation finished, connection will close |

```bash
curl -N http://localhost:8080/flights/$FLIGHT_ID/stream
```

**Example event output:**

```
id: 550e8400-e29b-41d4-a716-446655440000
event: METRICS
retry: 3000
comment: flight:1
data: {"flightId":1,"phase":"CRUISE","altitudeFeet":37000.0,...}

event: COMPLETED
comment: Flight 1 simulation finished
```

History is replayed from the database when the stream is opened, ensuring
clients that connect mid-flight see all previous snapshots before receiving
live events.

---

## Simulation Design

### Flight Phases & Duration

| Phase | Simulated Duration |
|-------|--------------------|
| BOARDING | 30 min |
| TAXI_OUT | 15 min |
| TAKEOFF_CLIMB | 25 min |
| CRUISE | 210 min (3.5 h) |
| DESCENT | 25 min |
| LANDING | 5 min |
| TAXI_IN | 10 min |
| **Total** | **320 min** |

All durations are configurable via `simulation.phases.*` in `application.yml`.

### Metric Modelling

| Metric | Model |
|--------|-------|
| **Altitude** | 0 on ground → linear climb to 37,000 ft → held at cruise → linear descent to 0 |
| **Airspeed** | 0 → 15 kts taxi → ramp to 480 kts cruise → decelerate on descent/landing → 10 kts taxi in |
| **Heading** | ~66° great-circle bearing LAX→JFK + small sinusoidal variation |
| **Position** | Linear interpolation between LAX (33.94°N, 118.41°W) and JFK (40.64°N, 73.78°W) |
| **Fuel** | Linear burn at 0.28% per simulated minute, floor at 0% |
| **OAT** | Standard atmosphere: 15°C at sea level, −2°C per 1,000 ft, floor at −56.5°C |
| **ETA** | Remaining airborne simulated minutes until end of LANDING phase |

---

## Configuration

All settings can be overridden via environment variables or command-line arguments:

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.r2dbc.url` | `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost:5432/flightdb` | PostgreSQL R2DBC URL |
| `spring.r2dbc.username` | `DB_USER` | `flight` | DB username |
| `spring.r2dbc.password` | `DB_PASSWORD` | `flight` | DB password |
| `simulation.time-multiplier` | `SIMULATION_TIME_MULTIPLIER` | `60` | Simulated minutes per real second |
| `simulation.tick-interval-seconds` | `SIMULATION_TICK_INTERVAL_SECONDS` | `5` | Real seconds between metric snapshots |
| `simulation.max-active-flights` | `SIMULATION_MAX_ACTIVE_FLIGHTS` | `10` | Max concurrent active flights |
| `simulation.phases.boarding` | — | `30` | Boarding phase duration (minutes) |
| `simulation.phases.taxi-out` | — | `15` | Taxi out duration (minutes) |
| `simulation.phases.takeoff-climb` | — | `25` | Takeoff/climb duration (minutes) |
| `simulation.phases.cruise` | — | `210` | Cruise duration (minutes) |
| `simulation.phases.descent` | — | `25` | Descent duration (minutes) |
| `simulation.phases.landing` | — | `5` | Landing duration (minutes) |
| `simulation.phases.taxi-in` | — | `10` | Taxi in duration (minutes) |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |

---

## Running Tests

All unit tests are self-contained — no Docker, no database required.
Integration tests use **embedded PostgreSQL** via `@DynamicPropertySource`.

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.flight.simulator.service.PropertiesPhaseServiceTest"

# Run with console output
./gradlew test --info

# Run and open HTML report
./gradlew test && open build/reports/tests/test/index.html
```

### Test Coverage by Layer

| Test Class | Layer | Strategy |
|------------|-------|----------|
| `PropertiesPhaseServiceTest` | Service | Real objects — no mocks |
| `FlightSimulationServiceTest` | Service | Real objects — no mocks |
| `MetricMapperTest` | Mapper | Real objects — no mocks |
| `FlightMapperTest` | Mapper | Mockito — mocks `SimulationProperties` |
| `FlightServiceTest` | Service | Mockito + StepVerifier |
| `FlightProcessorSchedulerTest` | Scheduler | Mockito + StepVerifier |
| `FlightStreamRegistryTest` | Stream | Mockito + StepVerifier |
| `FlightControllerTest` | Controller | `@WebFluxTest` + MockBean |
| `FlightControllerIntegrationTest` | Integration | Embedded PostgreSQL + WebTestClient |

---

## Design Decisions & Assumptions

### Reactive Stack — WebFlux + R2DBC
Spring WebFlux with Project Reactor was chosen to serve SSE streams efficiently without
dedicating a thread per open connection. R2DBC provides the matching non-blocking
PostgreSQL driver, keeping the entire request path non-blocking end to end.

### `boolean active` instead of a status enum
The flight lifecycle has exactly two states: in-progress and done.
A `boolean active` field maps directly to a SQL `BOOLEAN` column and makes
repository queries (`findByActive(true)`) self-documenting. If additional states
(e.g. `CANCELLED`, `DIVERTED`) are ever needed, this is the first thing to revisit.

### In-Memory SSE Sinks
Each active flight owns a `Sinks.Many<MetricResponse>` (Reactor multicast).
All historical snapshots are replayed from PostgreSQL when a new subscriber connects,
so late-joining clients receive full context. A production system would replace the
in-memory map with Redis Pub/Sub to support horizontal scaling.

### Mapper Layer
`FlightMapper` and `MetricMapper` own all conversions between domain objects and DTOs.
DTOs carry no knowledge of the domain model — the mapper is the only class that changes
when either side evolves. `toResponse` methods on the mapper are independently unit-tested
without needing a running Spring context.
