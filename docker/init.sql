-- ============================================================
-- Sentinel AI — Database Initialization Script
-- ============================================================

-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── Log Events ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS log_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT uuid_generate_v4() UNIQUE,
    timestamp       TIMESTAMP NOT NULL,
    service_name    VARCHAR(100) NOT NULL,
    log_level       VARCHAR(10) NOT NULL,        -- ERROR, WARN, INFO, DEBUG
    message         TEXT NOT NULL,
    stack_trace     TEXT,
    request_id      VARCHAR(64),
    latency_ms      INTEGER,
    status_code     INTEGER,
    host            VARCHAR(100),
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT NOW()
);

-- ─── Anomalies ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS anomalies (
    id              BIGSERIAL PRIMARY KEY,
    anomaly_id      UUID NOT NULL DEFAULT uuid_generate_v4() UNIQUE,
    detected_at     TIMESTAMP NOT NULL,
    service_name    VARCHAR(100) NOT NULL,
    anomaly_type    VARCHAR(50) NOT NULL,        -- ERROR_SPIKE, LATENCY_SURGE, AVAILABILITY_DROP
    severity        VARCHAR(5) NOT NULL,         -- P0, P1, P2, P3
    metric_name     VARCHAR(100),
    expected_value  DOUBLE PRECISION,
    actual_value    DOUBLE PRECISION,
    z_score         DOUBLE PRECISION,
    window_minutes  INTEGER DEFAULT 5,
    raw_data        JSONB DEFAULT '{}',
    status          VARCHAR(20) DEFAULT 'DETECTED',  -- DETECTED, ANALYZING, RESOLVED
    created_at      TIMESTAMP DEFAULT NOW()
);

-- ─── Incidents (with RCA + vector embedding) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS incidents (
    id                  BIGSERIAL PRIMARY KEY,
    incident_id         UUID NOT NULL DEFAULT uuid_generate_v4() UNIQUE,
    anomaly_id          UUID REFERENCES anomalies(anomaly_id),
    title               VARCHAR(500) NOT NULL,
    severity            VARCHAR(5) NOT NULL,
    status              VARCHAR(20) DEFAULT 'OPEN',  -- OPEN, ANALYZING, RESOLVED, FALSE_POSITIVE
    service_name        VARCHAR(100) NOT NULL,

    -- RCA (populated by LLM)
    rca_summary         TEXT,
    root_cause          TEXT,
    impact_analysis     TEXT,
    suggested_fix       TEXT,
    prevention          TEXT,
    confidence          DOUBLE PRECISION,

    -- Vector embedding for similarity search (1536 dim for OpenAI, 768 for smaller models)
    embedding           vector(1536),

    -- Timestamps
    detected_at         TIMESTAMP NOT NULL,
    analyzed_at         TIMESTAMP,
    resolved_at         TIMESTAMP,
    mttr_seconds        INTEGER,                 -- Auto-calculated on resolve

    -- Context
    related_logs        JSONB DEFAULT '[]',
    similar_incidents   JSONB DEFAULT '[]',

    created_at          TIMESTAMP DEFAULT NOW()
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_log_events_service_ts
    ON log_events(service_name, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_log_events_level
    ON log_events(log_level, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_log_events_timestamp
    ON log_events(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_anomalies_service_ts
    ON anomalies(service_name, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_anomalies_severity
    ON anomalies(severity, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_anomalies_status
    ON anomalies(status);

CREATE INDEX IF NOT EXISTS idx_incidents_status_severity
    ON incidents(status, severity);

CREATE INDEX IF NOT EXISTS idx_incidents_service
    ON incidents(service_name, detected_at DESC);

-- pgvector index for fast cosine similarity search
-- NOTE: Created after first data load for better index quality
-- CREATE INDEX idx_incidents_embedding ON incidents
--     USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ─── Seed Data (sample services for simulator) ───────────────────────────────
-- No seed data needed — simulator generates everything dynamically
