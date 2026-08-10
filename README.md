<p align="center">
  <h1 align="center">🛡️ Sentinel AI</h1>
  <p align="center">
    <strong>AI-Powered Incident Detection & Root Cause Analysis Platform</strong>
  </p>
  <p align="center">
    Real-time anomaly detection across microservices with LLM-driven root cause analysis, chaos engineering, and an interactive operations dashboard.
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white" alt="Java 21"/>
    <img src="https://img.shields.io/badge/Spring%20Boot-3.3.2-6DB33F?style=flat&logo=springboot&logoColor=white" alt="Spring Boot"/>
    <img src="https://img.shields.io/badge/React-19-61DAFB?style=flat&logo=react&logoColor=black" alt="React 19"/>
    <img src="https://img.shields.io/badge/Apache%20Kafka-Streaming-231F20?style=flat&logo=apachekafka&logoColor=white" alt="Kafka"/>
    <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white" alt="PostgreSQL"/>
    <img src="https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white" alt="Redis"/>
    <img src="https://img.shields.io/badge/LLM-Gemini%20|%20Llama-8E75B2?style=flat&logo=google&logoColor=white" alt="LLM"/>
  </p>
</p>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [Architecture](#-architecture)
- [System Architecture Diagram](#-system-architecture-diagram)
- [Module Breakdown](#-module-breakdown)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Infrastructure Setup](#1-infrastructure-setup)
  - [Backend Setup](#2-backend-setup)
  - [Frontend Setup](#3-frontend-setup)
  - [LLM Configuration](#4-llm-configuration)
- [Usage Guide](#-usage-guide)
- [Anomaly Detection Engine](#-anomaly-detection-engine)
- [AI-Powered Root Cause Analysis](#-ai-powered-root-cause-analysis)
- [Chaos Engineering](#-chaos-engineering)
- [API Reference](#-api-reference)
- [Dashboard](#-dashboard)
- [Data Pipeline](#-data-pipeline)
- [Configuration Reference](#-configuration-reference)
- [Testing](#-testing)
- [Project Structure](#-project-structure)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌟 Overview

**Sentinel AI** is a full-stack, production-grade incident management platform that demonstrates how modern SRE (Site Reliability Engineering) teams can leverage AI to dramatically reduce Mean Time To Resolution (MTTR).

The platform continuously monitors microservice traffic in real time, applies three independent statistical anomaly detection algorithms to identify incidents, and then uses Large Language Models (Google Gemini or Meta Llama) to automatically perform Root Cause Analysis — turning raw log noise into actionable engineering insights in seconds.

### The Problem

In a microservices architecture, when an incident occurs:
1. **Alert fatigue**: Monitoring tools fire dozens of overlapping alerts
2. **Slow triage**: Engineers manually sift through thousands of log lines across multiple services
3. **Knowledge silos**: Root cause diagnosis depends on who is on-call and their tribal knowledge
4. **Slow MTTR**: The entire process from alert → diagnosis → fix can take hours

### The Solution

Sentinel AI automates the entire incident lifecycle:

```
Log Stream → Statistical Detection → AI Root Cause Analysis → Actionable Incident
```

An anomaly that would take an engineer 45 minutes to manually diagnose is analyzed by the AI in under 10 seconds — complete with root cause identification, impact assessment, remediation steps, and prevention strategies.

---

## ✨ Key Features

### 🔍 Multi-Algorithm Anomaly Detection
- **Z-Score Analysis**: Detects statistical outliers by measuring standard deviations from the rolling mean across latency, error counts, and 5xx/429 status codes
- **Error Rate Threshold Detection**: Monitors pre-computed error rates against configurable P0/P1/P2 severity thresholds (20%/10%/5%)
- **Moving Average Deviation**: Identifies gradual latency degradation by comparing current values against a 15-minute moving baseline

### 🤖 LLM-Powered Root Cause Analysis
- Automatically gathers contextual logs (±5 minutes around the anomaly timestamp)
- Constructs structured prompts with anomaly metadata and raw log evidence
- Sends to **Google Gemini 2.5 Flash** (via OpenRouter) or **Meta Llama 3** (via local Ollama)
- Parses structured JSON responses into actionable incident reports
- **Auto-Healing Model Selection**: Dynamically discovers and falls back through all available free models on OpenRouter if the primary model becomes unavailable
- **Smart Caching**: Caches LLM responses in Redis for 1 hour to reduce API costs and latency
- **Rate Limiting**: Built-in rate limiter (15 req/min) to stay within free-tier limits

### 📊 Real-Time Operations Dashboard
- Live system health overview using industry-standard **RED metrics** (Rate, Errors, Duration)
- **P99 Latency monitoring** — not averages — matching how Google SRE, AWS, and Datadog measure real service health
- Interactive incident management with full lifecycle support (Open → Analyzing → Resolved/Dismissed)
- Real-time anomaly detection graph with live WebSocket-style polling
- Service-level health indicators that dynamically reflect error rates AND latency percentiles

### 🧪 Chaos Engineering Toolkit
- **7 pre-built chaos scenarios**: Error Spike, Latency Surge, Database Outage, Memory Leak, Downstream Failure, Rate Limit Spike, Configuration Error
- Target any of the 5 monitored microservices independently
- Generates realistic log patterns with authentic stack traces, status codes, and latency profiles
- Controlled environment to test and validate the detection and RCA pipeline end-to-end

### 🔐 Secure API Key Management
- AES-256-GCM encryption for API keys stored in Redis
- Masked key display in the UI (e.g., `sk-or-...x4Bf`)
- One-click clear and connection testing
- Dual key source: Dashboard settings (encrypted in Redis) or environment variable fallback

### 🛠️ Manual Triage & Analyst Workflow
- Fallback manual triage for incidents where AI analysis returns `UNKNOWN` root cause
- Editable disposition fields (root cause, impact, fix, prevention)
- Full incident lifecycle: Accept → Resolve / Dismiss / Close
- Comment system for collaboration

### 📈 System Health Monitoring (RED Method)
- **Rate**: Request throughput per service
- **Errors**: Error rate percentage
- **Duration**: P99 latency with thresholds:
  - `> 150ms` → ⚠️ WARNING
  - `> 250ms` → 🟠 DEGRADED
  - `> 500ms` → 🔴 CRITICAL
  - `> 30% error rate` → 🔴 CRITICAL

### 🗄️ Data Retention & Management
- Automated daily cleanup cron (midnight) for logs and anomalies older than 24 hours
- Incident cap of 1,000 records with automatic pruning
- Factory reset capability for clean demo environments

---

## 🏗 Architecture

Sentinel AI follows an **event-driven microservices architecture** with clear separation of concerns:

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         SENTINEL AI PLATFORM                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌──────────────┐    Kafka: log-events    ┌──────────────────┐     │
│   │  Simulation  │ ──────────────────────► │    Ingestion     │     │
│   │  Engine      │                         │    Service       │     │
│   │  (Traffic +  │                         │  ┌────────────┐  │     │
│   │   Chaos)     │                         │  │ Batch JDBC │  │     │
│   └──────────────┘                         │  │  Insert    │  │     │
│                                            │  └─────┬──────┘  │     │
│                                            │        │         │     │
│                                            │  ┌─────▼──────┐  │     │
│                                            │  │ Real-Time  │  │     │
│                                            │  │ Metrics    │  │     │
│                                            │  │ (Redis)    │  │     │
│                                            │  └────────────┘  │     │
│                                            └──────────────────┘     │
│                                                                     │
│   ┌──────────────────┐  Kafka: anomaly-events  ┌───────────────┐    │
│   │    Detector      │ ─────────────────────►  │     RCA       │    │
│   │  ┌─────────────┐ │                         │   Service     │    │
│   │  │ Z-Score     │ │                         │ ┌───────────┐ │    │
│   │  │ Error Rate  │ │  Kafka: rca-retry       │ │  Context  │ │    │
│   │  │ Moving Avg  │ │ ◄────────────────────── │ │  Gatherer │ │    │
│   │  └─────────────┘ │                         │ ├───────────┤ │    │
│   │  Reads from Redis│                         │ │  Prompt   │ │    │
│   │  (health:{svc})  │                         │ │  Builder  │ │    │
│   └──────────────────┘                         │ ├───────────┤ │    │
│                                                │ │ OpenRouter│ │    │
│                                                │ │ / Ollama  │ │    │
│   ┌──────────────────┐                         │ └───────────┘ │    │
│   │   REST API       │ ◄─── HTTP ───────────── │               │    │
│   │   (sentinel-api) │                         └───────────────┘    │
│   └────────┬─────────┘                                              │
│            │                                                        │
│     ┌──────▼──────┐                                                 │
│     │  Dashboard  │                                                 │
│     │  (React)    │                                                 │
│     └─────────────┘                                                 │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  Infrastructure: PostgreSQL 16 (pgvector) │ Redis 7 │ Kafka 7.5     │
└─────────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Traffic Generation**: The `SimulationService` generates continuous normal microservice traffic (INFO/WARN logs) at a configurable rate. The `ChaosService` injects targeted anomalous patterns.

2. **Ingestion**: `sentinel-ingestion` consumes from the `log-events` Kafka topic, performs high-throughput batch JDBC inserts into PostgreSQL (batch size 50), and simultaneously updates **real-time sliding window metrics** in Redis sorted sets.

3. **Detection**: `sentinel-detector` runs a scheduled detection cycle every 30 seconds. For each of the 5 monitored services, it runs all three anomaly detection algorithms against the Redis metrics. Detected anomalies are published to the `anomaly-events` Kafka topic with a 5-minute deduplication window to prevent alert storms.

4. **Root Cause Analysis**: `sentinel-rca` consumes anomaly events, gathers contextual log evidence from PostgreSQL, constructs a structured prompt, and queries the LLM. The AI response is parsed into a structured incident record (root cause, impact, fix, prevention) and persisted to PostgreSQL.

5. **Dashboard**: The React frontend polls the `sentinel-api` REST layer for incidents, health status, and statistics, providing a real-time operational view.

---

## 📦 Module Breakdown

| Module | Port | Description |
|--------|------|-------------|
| `sentinel-common` | — | Shared DTOs (`LogEventDTO`, `AnomalyDTO`), enums (`LogLevel`, `Severity`, `AnomalyType`), and data models used across all backend services |
| `sentinel-simulator` | 8085 | Standalone traffic generation engine (can run independently) |
| `sentinel-ingestion` | 8082 | Kafka consumer for log events → PostgreSQL batch insert + Redis real-time metrics (p99 latency, error rate, request count) |
| `sentinel-detector` | 8084 | Scheduled anomaly detection engine with Z-Score, Error Rate, and Moving Average analysers |
| `sentinel-rca` | 8083 | AI-powered Root Cause Analysis — consumes anomaly events, queries LLMs, generates structured incident reports |
| `sentinel-alert` | 8086 | Notification/alerting module (extensible for Slack, PagerDuty, email integrations) |
| `sentinel-api` | 8080 | Central REST API — orchestrates simulation, chaos injection, incident management, health monitoring, and settings |
| `sentinel-dashboard` | 5173 | React 19 + Vite frontend — real-time operations dashboard with incident management, health visualization, and chaos controls |

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Core language with modern features (records, text blocks, pattern matching) |
| Spring Boot | 3.3.2 | Application framework for all backend services |
| Spring Kafka | 3.3.2 | Event-driven communication between services |
| Spring Data JPA | 3.3.2 | PostgreSQL ORM with Hibernate |
| Spring Data Redis | 3.3.2 | Real-time metrics, caching, distributed locks |
| Lombok | — | Boilerplate reduction |
| JaCoCo | 0.8.12 | Test coverage reporting |
| Testcontainers | 1.20.1 | Integration testing with real infrastructure |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 19.2.8 | UI framework |
| Vite | 8.2.0 | Build tool and dev server |
| Recharts | 3.10.1 | Interactive data visualization charts |
| Lucide React | 1.28.0 | Modern icon library |
| Jest | 30.4.2 | Unit testing framework |
| Testing Library | 16.3.2 | React component testing utilities |

### Infrastructure
| Technology | Version | Purpose |
|---|---|---|
| PostgreSQL | 16 (pgvector) | Primary data store with vector similarity search support |
| Apache Kafka | 7.5.0 (Confluent) | Event streaming backbone |
| Redis | 7-alpine | Real-time metrics, caching, distributed locking, health state |
| Zookeeper | 7.5.0 | Kafka cluster coordination |
| Docker Compose | — | One-command infrastructure provisioning |

### AI / LLM
| Provider | Model | Purpose |
|---|---|---|
| OpenRouter | Google Gemini 2.5 Flash (free) | Primary AI provider for RCA generation |
| Ollama (local) | Meta Llama 3 | Offline/local fallback provider |

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** (JDK) — [Eclipse Adoptium](https://adoptium.net/)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **Node.js 20+** & **npm** — [Download](https://nodejs.org/)
- **Docker** & **Docker Compose** — [Download](https://docs.docker.com/get-docker/)
- **Git** — [Download](https://git-scm.com/)

### 1. Infrastructure Setup

Clone the repository and start the infrastructure services:

```bash
git clone https://github.com/Sarajis99/sentinel-ai.git
cd sentinel-ai

# Start PostgreSQL, Redis, Kafka, Zookeeper, and monitoring UIs
docker-compose up -d
```

This provisions:
- **PostgreSQL** on `localhost:5432` (auto-creates database schema via `init.sql`)
- **Redis** on `localhost:6379` (256MB with LRU eviction)
- **Kafka** on `localhost:9092` (with auto-topic creation enabled)
- **Kafka UI** on `localhost:8090` (web-based Kafka visualizer)
- **Redis Commander** on `localhost:8081` (web-based Redis visualizer)

Verify everything is healthy:

```bash
docker-compose ps
```

### 2. Backend Setup

Build all backend modules from the project root:

```bash
mvn clean install -DskipTests
```

Start each service (in separate terminals or your IDE):

```bash
# Terminal 1: API Gateway (must start first)
cd sentinel-api && mvn spring-boot:run

# Terminal 2: Ingestion Pipeline
cd sentinel-ingestion && mvn spring-boot:run

# Terminal 3: Anomaly Detector
cd sentinel-detector && mvn spring-boot:run

# Terminal 4: RCA Engine
cd sentinel-rca && mvn spring-boot:run
```

> **💡 Tip**: If using IntelliJ IDEA, create Run Configurations for each module. The services can be started in any order after `sentinel-api`.

### 3. Frontend Setup

```bash
cd sentinel-dashboard

# Install dependencies
npm install

# Start development server
npm run dev
```

The dashboard will be available at **http://localhost:5173**

### 4. LLM Configuration

Sentinel AI supports two LLM providers:

#### Option A: OpenRouter (Recommended — Free Tier)

1. Sign up at [openrouter.ai](https://openrouter.ai/) and generate an API key
2. Configure via one of these methods:

   **Method 1 — Dashboard UI**: Navigate to Settings → paste your API key → Save

   **Method 2 — Environment Variable**: Set `OPENROUTER_API_KEY` in the `sentinel-rca` environment:
   ```bash
   export OPENROUTER_API_KEY=sk-or-v1-your-key-here
   ```

#### Option B: Ollama (Local, Offline)

1. Install [Ollama](https://ollama.com/)
2. Pull the Llama 3 model: `ollama pull llama3`
3. Set the environment variable: `LLM_PROVIDER=ollama`
4. Start `sentinel-rca` — it will connect to `http://localhost:11434`

---

## 📘 Usage Guide

### Quick Start Demo (5 Minutes)

1. **Open the Dashboard**: Navigate to `http://localhost:5173`
2. **Start Simulation**: Click the **"Start Simulation"** button — this begins generating realistic microservice traffic across 5 services
3. **Observe Health**: Watch the System Health Overview as real-time metrics stream in. You'll see p99 latency and error rates update live
4. **Inject Chaos**: Open the Chaos panel, select a scenario (e.g., "Database Outage"), target "inventory-service", and click **Inject**
5. **Watch Detection**: Within 30 seconds, the anomaly detector will identify the statistical deviation and create an incident
6. **AI Analysis**: If an API key is configured, the RCA engine will automatically analyze the incident within seconds, providing root cause, impact, fix, and prevention
7. **Review Incident**: Click the incident in the table to see the full AI-generated analysis
8. **Resolve**: Accept or dismiss the incident through the dashboard

### Incident Lifecycle

```text
NEW → ANALYZING → OPEN → ACCEPTED → RESOLVED
                    │                    │
                    └─→ DISMISSED        └─→ CLOSED
                    │
                    └─→ MANUAL TRIAGE (if AI returns UNKNOWN)
```

---

## 🔬 Anomaly Detection Engine

The detector runs a multi-layered analysis cycle every 30 seconds against a 5-minute rolling window of metrics stored in Redis:

### 1. Z-Score Analyser

Detects **statistical outliers** by measuring how many standard deviations the latest data point deviates from the rolling mean.

| Z-Score | Severity | Meaning |
|---------|----------|---------|
| ≥ 5.0 | P0 (Critical) | Extreme outlier — 1 in 3.5 million chance of being normal |
| ≥ 3.0 | P1 (High) | Significant deviation — 99.7% confidence of anomaly |
| ≥ 2.0 | P2 (Moderate) | Notable deviation — 95.5% confidence of anomaly |

**Monitored metrics**: `latency_ms`, `error_5xx_count`, `error_count`, `error_429_count`

### 2. Error Rate Analyser

Monitors the **pre-computed error rate** (errors ÷ total requests) from the Redis health hash.

| Error Rate | Severity |
|------------|----------|
| ≥ 20% | P0 (Critical) |
| ≥ 10% | P1 (High) |
| ≥ 5% | P2 (Moderate) |

Requires a minimum of 10 requests in the window to avoid false positives during cold start.

### 3. Moving Average Analyser

Detects **gradual latency degradation** by comparing the current latency against a 15-minute moving baseline.

| Deviation | Severity |
|-----------|----------|
| > 50% above baseline | P2 (Moderate) |

### Deduplication

A Redis-based deduplication mechanism prevents the same anomaly type from being re-published within a 5-minute window, preventing alert storms during sustained incidents.

---

## 🤖 AI-Powered Root Cause Analysis

### How It Works

When the Detector publishes an anomaly to the `anomaly-events` Kafka topic:

1. **Context Gathering**: The `ContextGatherer` queries PostgreSQL for up to 50 log entries from the affected service within ±5 minutes of the anomaly timestamp

2. **Prompt Engineering**: The `PromptBuilder` constructs a structured prompt containing:
   - Anomaly metadata (service, severity, metric, expected vs actual values, % deviation)
   - Raw log evidence (timestamps, log levels, messages, stack traces, status codes)
   - Explicit instructions to identify the TRUE root cause (not just symptoms)

3. **LLM Invocation**: The prompt is sent to the configured LLM provider. The `OpenRouterClient` implements an auto-healing mechanism:
   - Tries the configured default model first
   - On failure, fetches the live list of all free models from OpenRouter's `/models` endpoint
   - Automatically retries with each fallback model until one succeeds

4. **Response Parsing**: The JSON response is parsed into structured fields:
   - `rca_summary`: Executive summary of the incident
   - `root_cause`: Technical root cause identification
   - `impact_analysis`: User and business impact assessment
   - `suggested_fix`: Step-by-step remediation instructions
   - `prevention`: Long-term prevention strategies
   - `confidence`: AI confidence score (0.0 - 1.0)

5. **Caching**: Successful RCA results are cached in Redis (1-hour TTL) keyed by anomaly fingerprint. Subsequent identical anomalies receive instant cached responses.

### Retry Mechanism

If the LLM call fails (network timeout, rate limit, etc.), the anomaly is republished to the `rca-retry-events` Kafka topic for a delayed retry.

---

## 🧪 Chaos Engineering

The chaos toolkit provides 7 pre-built scenarios that generate realistic failure patterns:

| Scenario | Description | Log Pattern |
|----------|-------------|-------------|
| **Error Spike** | Sudden burst of 40-50 unhandled exceptions | `500` status, `RuntimeException` stack traces, 5-7s latency |
| **Latency Surge** | Gradual performance degradation | `200`/`503` status, 3-8s latency, WARN-level messages |
| **Database Outage** | Complete database connection pool exhaustion | `503` status, `HikariPool` stack traces, 30s timeout |
| **Memory Leak** | Progressive heap exhaustion ending in OOM | Escalating latency (200ms → 3.8s), eventual `OutOfMemoryError` |
| **Downstream Failure** | Cascading timeout from a dependent service | `504` status, `SocketTimeoutException`, 5-8s latency |
| **Rate Limit Spike** | External API rate limiting | `429` status, retry-after headers, low latency |
| **Configuration Error** | Bad deployment with invalid JWT keys | `401`/`403` status, auth validation failures |

### Target Services

All 5 monitored microservices are available as chaos targets:
- `payment-service`
- `order-service`
- `inventory-service`
- `notification-service`
- `user-service`

---

## 📡 API Reference

Base URL: `http://localhost:8080/api/v1`

### Incidents

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/incidents` | List incidents (paginated, filterable by status/severity/service) |
| `GET` | `/incidents/{id}` | Get incident details with full RCA |
| `GET` | `/incidents/unknown` | Get incidents requiring manual triage |
| `GET` | `/incidents/stats` | Aggregated incident statistics |
| `POST` | `/incidents/{id}/accept` | Accept an incident for investigation |
| `POST` | `/incidents/{id}/resolve` | Mark incident as resolved |
| `POST` | `/incidents/{id}/dismiss` | Dismiss as false positive |
| `POST` | `/incidents/{id}/close` | Close a resolved incident |
| `PUT` | `/incidents/{id}/manual-disposition` | Submit manual triage analysis |
| `POST` | `/incidents/{id}/retry-analysis` | Retry AI analysis for a failed incident |
| `GET` | `/incidents/{id}/comments` | Get incident comments |
| `POST` | `/incidents/{id}/comments` | Add a comment to an incident |

### Simulation & Chaos

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/system/simulate` | Start normal traffic simulation |
| `POST` | `/system/simulate/stop` | Stop the running simulation |
| `GET` | `/system/simulation-status` | Check if simulation is active |
| `POST` | `/chaos/inject` | Inject a chaos scenario (`{ "scenario": "DB_OUTAGE", "serviceName": "inventory-service" }`) |
| `GET` | `/chaos/scenarios` | List available chaos scenarios |
| `GET` | `/chaos/services` | List targetable services |

### System & Settings

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | System health check (infrastructure + per-service RED metrics) |
| `GET` | `/settings/api-key` | Get masked API key status |
| `PUT` | `/settings/api-key` | Update or clear the OpenRouter API key |
| `GET` | `/settings/test-llm` | Test LLM connectivity |
| `GET` | `/settings/simulation` | Get simulation configuration |
| `PUT` | `/settings/simulation` | Update logs-per-second rate |
| `POST` | `/system/retention-cleanup` | Trigger manual data retention cleanup |
| `POST` | `/system/factory-reset` | Full factory reset (truncates all data) |

---

## 🖥 Dashboard

The operations dashboard provides a real-time view of the entire platform:

### Pages

| Page | Features |
|------|----------|
| **Dashboard** | Live incident feed, anomaly detection graph, system health overview (RED metrics), incident severity distribution, real-time service health indicators |
| **Incidents** | Searchable/filterable incident table, full incident detail view with AI analysis, manual triage workflow, comment system, lifecycle actions |
| **Simulation** | One-click simulation start/stop, chaos scenario injection panel, service targeting, logs-per-second configuration |
| **Settings** | API key management (save/clear/test), simulation configuration, factory reset, data retention controls |

---

## 🔄 Data Pipeline

### Kafka Topics

| Topic | Producer | Consumer | Payload |
|-------|----------|----------|---------|
| `log-events` | SimulationService, ChaosService | sentinel-ingestion | `LogEventDTO` |
| `anomaly-events` | AnomalyPublisher (detector) | AnomalyEventConsumer (RCA) | `AnomalyDTO` |
| `rca-retry-events` | RCAService | RetryRCAConsumer | `AnomalyDTO` |

### Redis Data Structures

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `metrics:{service}:{metric}` | Sorted Set | Time-series metric values (score = timestamp) | 12 min |
| `health:{service}` | Hash | Pre-computed health summary (error_rate, p99_latency, request_count) | 12 min |
| `dedup:anomaly:{service}:{type}` | String | Anomaly deduplication flag | 5 min |
| `rca:cache:{fingerprint}` | String | Cached LLM RCA responses | 1 hour |
| `simulator:active_lock` | String | Distributed simulation lock | 15 min |
| `settings:openrouter-api-key` | String | AES-256-GCM encrypted API key | — |

### PostgreSQL Schema

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `log_events` | Raw log storage | event_id, service_name, log_level, message, latency_ms, status_code |
| `anomalies` | Detected anomaly records | anomaly_id, service_name, severity, z_score, expected/actual values |
| `incidents` | Incident records with RCA | incident_id, rca_summary, root_cause, impact_analysis, suggested_fix, confidence, embedding (vector) |
| `incident_comments` | Collaboration thread | incident_id, author, content |

---

## ⚙️ Configuration Reference

### Detection Thresholds (`sentinel-detector`)

```yaml
detection:
  window-minutes: 5                    # Rolling detection window
  detector-interval-ms: 30000         # Detection cycle frequency
  min-requests-for-detection: 10      # Minimum events before triggering
  
  # Z-Score thresholds
  z-score-p0-threshold: 5.0           # 5σ → P0
  z-score-p1-threshold: 3.0           # 3σ → P1
  z-score-p2-threshold: 2.0           # 2σ → P2
  
  # Error rate thresholds
  error-rate-p0-threshold: 0.20       # 20% → P0
  error-rate-p1-threshold: 0.10       # 10% → P1
  error-rate-p2-threshold: 0.05       #  5% → P2
  
  # Latency deviation
  latency-deviation-threshold: 0.50   # 50% above moving avg → anomaly
  moving-average-lookback-minutes: 15
```

### LLM Configuration (`sentinel-rca`)

```yaml
llm:
  provider: openrouter                 # openrouter | ollama
  openrouter:
    model: google/gemini-2.5-flash:free
    timeout-seconds: 45
    max-retries: 2
  ollama:
    model: llama3
    timeout-seconds: 120
  cache:
    enabled: true
    ttl-seconds: 3600
  rate-limit:
    max-per-minute: 15
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENROUTER_API_KEY` | — | OpenRouter API key for LLM access |
| `LLM_PROVIDER` | `openrouter` | LLM provider selection (`openrouter` or `ollama`) |
| `OPENROUTER_MODEL` | `google/gemini-2.5-flash:free` | Default LLM model |
| `SETTINGS_ENCRYPTION_KEY` | `sentinel-ai-default-encryption-key-32b` | AES encryption key for API key storage |

---

## 🧪 Testing

The project uses a multi-layer testing strategy:

### Backend Tests

```bash
# Run all backend tests
mvn test

# Run tests for a specific module
mvn test -pl :sentinel-detector
mvn test -pl :sentinel-rca
mvn test -pl :sentinel-api

# Generate test coverage report
mvn verify
# Reports available at: target/site/jacoco/index.html
```

### Frontend Tests

```bash
cd sentinel-dashboard

# Run all tests
npm test

# Run with coverage
npm test -- --coverage
```

### Testing Frameworks
- **Backend**: JUnit 5, Mockito, Spring `@WebMvcTest`, Testcontainers
- **Frontend**: Jest, React Testing Library, jsdom

---

## 📂 Project Structure

```text
sentinel-ai/
├── docker/
│   └── init.sql                     # PostgreSQL schema initialization
├── docker-compose.yml               # Infrastructure provisioning
├── pom.xml                          # Parent Maven POM (multi-module)
│
├── sentinel-common/                 # Shared library
│   └── src/main/java/.../
│       ├── dto/                     # LogEventDTO, AnomalyDTO
│       └── enums/                   # LogLevel, Severity, AnomalyType
│
├── sentinel-ingestion/              # Log ingestion pipeline
│   └── src/main/java/.../
│       ├── consumer/                # KafkaLogConsumer
│       └── service/                 # BatchInsertService, RealTimeMetricsService
│
├── sentinel-detector/               # Anomaly detection engine
│   └── src/main/java/.../
│       ├── config/                  # DetectionConfig (thresholds)
│       ├── engine/                  # ZScoreAnalyser, ErrorRateAnalyser, MovingAverageAnalyser
│       ├── model/                   # AnomalySignal
│       └── service/                 # AnomalyDetector, AnomalyPublisher
│
├── sentinel-rca/                    # AI Root Cause Analysis
│   └── src/main/java/.../
│       ├── consumer/                # AnomalyEventConsumer, RetryRCAConsumer
│       ├── llm/                     # OpenRouterClient, OllamaClient, LLMClient
│       ├── model/                   # RCAResponse
│       └── service/                 # RCAService, ContextGatherer, PromptBuilder
│
├── sentinel-api/                    # REST API gateway
│   └── src/main/java/.../
│       ├── controller/              # IncidentController, ChaosController, HealthController, ...
│       ├── entity/                  # Incident, Anomaly, LogEvent JPA entities
│       ├── repository/              # Spring Data JPA repositories
│       └── service/                 # IncidentService, SimulationService, ChaosService, ...
│
├── sentinel-alert/                  # Alerting module (extensible)
│
├── sentinel-dashboard/              # React frontend
│   ├── src/
│   │   ├── App.jsx                  # Main application component
│   │   ├── api.js                   # API client utilities
│   │   ├── index.css                # Design system & styles
│   │   └── main.jsx                 # Application entry point
│   ├── package.json
│   └── vite.config.js
│
└── README.md
```

---

## 🤝 Contributing

Contributions are welcome! Here's how to get started:

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** your changes: `git commit -m 'Add amazing feature'`
4. **Push** to the branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Development Guidelines

- Follow existing code conventions and package structure
- Write tests for new features (aim for >80% coverage)
- Update documentation for API changes
- Use meaningful commit messages

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with ☕ Java, ⚛️ React, and 🤖 AI
  <br/>
  <strong>Sentinel AI</strong> — Because incidents shouldn't require tribal knowledge.
</p>
