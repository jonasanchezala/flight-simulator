DROP TABLE IF EXISTS flight_metrics;
DROP TABLE IF EXISTS flights;


CREATE TABLE IF NOT EXISTS flights (
    id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    origin           VARCHAR(4)   NOT NULL,
    destination      VARCHAR(4)   NOT NULL,
    airline          VARCHAR(100),
    flight_number    VARCHAR(10),
    phase            VARCHAR(30)   NOT NULL DEFAULT 'BOARDING',
    started_at       TIMESTAMPTZ   NOT NULL,
    completed_at     TIMESTAMPTZ,
    time_multiplier  INT           NOT NULL DEFAULT 60
);

CREATE TABLE IF NOT EXISTS flight_metrics (
    id                        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flight_id                 INT REFERENCES flights(id),
    recorded_at               TIMESTAMPTZ   NOT NULL,
    phase                     VARCHAR(30)   NOT NULL,
    altitude_feet             DOUBLE PRECISION NOT NULL,
    airspeed_knots            DOUBLE PRECISION NOT NULL,
    heading_degrees           DOUBLE PRECISION NOT NULL,
    latitude                  DOUBLE PRECISION NOT NULL,
    longitude                 DOUBLE PRECISION NOT NULL,
    fuel_percentage           DOUBLE PRECISION NOT NULL,
    outside_air_temp_celsius  DOUBLE PRECISION NOT NULL,
    eta_minutes               DOUBLE PRECISION NOT NULL
);
