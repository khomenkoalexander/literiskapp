# LiteRisk App — Deployment & Usage

## Prerequisites

- Docker + Docker Compose
- JARs built from project root:
  ```bash
  mvn package -DskipTests
  ```
  This produces `app/target/app.jar` and `cli/target/cli.jar`.

---

## Deployment

All commands run from the `docker/` directory.

### 1. Set environment variables

Create a `.env` file in `docker/`:

```env
BEARER_TOKEN=your-secret-token
DB_PASSWORD=literiskapp
```

`BEARER_TOKEN` is required — the app rejects all requests without it.

### 2. Start

```bash
docker compose up --build -d
```

First startup initialises the database schema automatically via `schema.sql`.

### 3. Stop

```bash
docker compose down
```

To also remove persisted data:

```bash
docker compose down -v
```

### 4. Logs

```bash
# Container stdout
docker compose logs -f app

# App log file (persisted volume)
docker compose exec app tail -f /app/logs/literiskapp.log
```

---

## Ports

| Service | Host port | Purpose |
|---------|-----------|---------|
| app     | 8082      | REST API |
| db      | 5434      | PostgreSQL (direct access) |

---

## REST API

All requests require the header:

```
Authorization: Bearer your-secret-token
```

Base URL: `http://localhost:8082`

Dates use ISO 8601 format: `"2024-01-31"`

---

### Deals

**List all**
```
GET /api/deals
```

**Insert** — body must be a JSON array
```
POST /api/deals
Content-Type: application/json

[
  {
    "id": "DEAL-001",
    "type": "LOAN",
    "dealDate": "2024-01-01",
    "maturityDate": "2026-01-01",
    "originalPrincipal": 1000000.00,
    "currency": "USD",
    "assetLiability": "ASSET",
    "nominalRate": 0.05,
    "bookValue": 1000000.00,
    "intPayFreq": "MONTHLY",
    "intPayStart": "2024-02-01",
    "prinPayFreq": "MONTHLY",
    "prinPayStart": "2024-02-01",
    "amortizationType": "LINEAR"
  }
]
```

**Truncate**
```
DELETE /api/deals
```
Returns `204 No Content`.

---

### Market data

**List all**
```
GET /api/markets
```

**Insert** — body must be a JSON array
```
POST /api/markets
Content-Type: application/json

[
  {
    "type": "INTEREST_RATE",
    "object": "USD_3M",
    "date": "2024-01-01",
    "value": 0.0525
  }
]
```

**Truncate**
```
DELETE /api/markets
```
Returns `204 No Content`.

---

### Cashflows *(read/truncate only)*

**List all**
```
GET /api/cashflows
```

**Truncate**
```
DELETE /api/cashflows
```
Returns `204 No Content`.

---

### Results *(read/truncate only)*

**List all**
```
GET /api/results
```

**Truncate**
```
DELETE /api/results
```
Returns `204 No Content`.

---

## CLI

The CLI is a self-contained JAR (`cli/target/cli.jar`) requiring a YAML config file.

### Config file

```yaml
baseUrl: http://localhost:8082
bearerToken: your-secret-token
```

### Usage

```
java -Dconfig=<config.yml> -jar cli.jar <entity> <command> [file=<path>]
```

Run without arguments to see the full command list.

### Commands

```bash
# Deals
java -Dconfig=config.yml -jar cli.jar deals list
java -Dconfig=config.yml -jar cli.jar deals insert file=deals.json
java -Dconfig=config.yml -jar cli.jar deals truncate

# Market data
java -Dconfig=config.yml -jar cli.jar markets list
java -Dconfig=config.yml -jar cli.jar markets insert file=markets.json
java -Dconfig=config.yml -jar cli.jar markets truncate

# Cashflows
java -Dconfig=config.yml -jar cli.jar cashflows list
java -Dconfig=config.yml -jar cli.jar cashflows truncate

# Results
java -Dconfig=config.yml -jar cli.jar results list
java -Dconfig=config.yml -jar cli.jar results truncate
```

### Insert file format

Files passed to `insert` must contain a JSON array:

`deals.json`:
```json
[
  {
    "id": "DEAL-001",
    "type": "LOAN",
    "dealDate": "2024-01-01",
    "maturityDate": "2026-01-01",
    "originalPrincipal": 1000000.00,
    "currency": "USD",
    "assetLiability": "ASSET",
    "nominalRate": 0.05,
    "bookValue": 1000000.00,
    "intPayFreq": "MONTHLY",
    "intPayStart": "2024-02-01",
    "prinPayFreq": "MONTHLY",
    "prinPayStart": "2024-02-01",
    "amortizationType": "LINEAR"
  }
]
```

`markets.json`:
```json
[
  { "type": "INTEREST_RATE", "object": "USD_3M", "date": "2024-01-01", "value": 0.0525 },
  { "type": "INTEREST_RATE", "object": "EUR_3M", "date": "2024-01-01", "value": 0.0390 }
]
```

---

## Volumes

| Volume | Contents |
|--------|----------|
| `literiskapp-db-data` | PostgreSQL data files |
| `literiskapp-app-logs` | Application log files |

Both survive `docker compose down`. Use `docker compose down -v` to remove them.
