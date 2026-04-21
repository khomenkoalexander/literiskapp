# Demo Spring Boot Cashflows / Financial Metrics generator.

* Docker deployment (docker compose)
* PostgreSQL DB
* REST API (JSON)
* Command Line tool

Typical usage:
* Import deals through one or more REST calls, or insert directly into DB
* Import market data through one or more REST calls, or insert directly into DB
* Trigger processing and wait for process to finish
* Get Cashflows (either from DB or via REST)
* Get Financial Metrics (either from DB or via REST)

REST endpoints:
```
GET /api/deals
POST /api/deals
DELETE /api/deals

GET /api/markets
POST /api/markets
DELETE /api/markets

POST /api/process
GET /api/process
GET /api/process/{id}

GET /api/cashflows
DELETE /api/cashflows

GET /api/results
DELETE /api/results
```



## Input:
Deals
```json
[
  {
    "id": "TEST-LOAN-USD",
    "type": "REGULAR_PAYMENT",
    "dealDate": "2024-01-01",
    "maturityDate": "2025-12-31",
    "originalPrincipal": 1200000.00,
    "currency": "USD",
    "assetLiability": "ASSET",
    "nominalRate": 0.05,
    "bookValue": 1200000.00,
    "intPayFreq": "MONTHLY",
    "intPayStart": "2024-02-01",
    "prinPayFreq": "MONTHLY",
    "prinPayStart": "2024-02-01",
    "amortizationType": "LINEAR"
  },
  {...}
]
```
Market
```json
[
  { "type": "FX", "object": "EUR/USD", "date": "2024-01-01", "dvalue": 1.0800 },
  { "type": "FX", "object": "EUR/USD", "date": "2024-02-01", "dvalue": 1.0820 },
  {...}
]
```

## Output:
Cashflows
```json
[
{
"id" : 129,
"deal" : "TEST-BOND",
"type" : "COUPON",
"date" : "2025-07-15",
"amount" : 24133.333333333332,
"currency" : "USD",
"remainingPrincipal" : 1000000.0,
"accruedInterest" : 0.0,
"bookValue" : 989000.0000000001,
"nominalRate" : 0.048,
"discountFactor" : 0.9360407747500966,
"npv" : 22589.784030635663
}, {
"id" : 130,
"deal" : "TEST-BOND",
"type" : "COUPON",
"date" : "2026-01-15",
"amount" : 24533.333333333332,
"currency" : "USD",
"remainingPrincipal" : 1000000.0,
"accruedInterest" : 0.0,
"bookValue" : 1000000.0,
"nominalRate" : 0.048,
"discountFactor" : 0.9200337345702675,
"npv" : 22571.494288123893
},
{...}
]
```
Financial Metrics
```json
[
  {
  "id" : 1,
  "assetLiability" : "ASSET",
  "interval" : "2024-02-01",
  "deal" : "TEST-LOAN-USD",
  "currency" : "USD",
  "bookValue" : 1095652.1739130435,
  "principalFlow" : 104347.82608695653,
  "interestIncome" : 5166.666666666667,
  "interestExpense" : 0.0,
  "couponIncome" : 0.0,
  "fxPnl" : 0.0,
  "npv" : 109026.39993187689,
  "nominalBalance" : 1095652.1739130435
}, {
  "id" : 2,
  "assetLiability" : "ASSET",
  "interval" : "2024-03-01",
  "deal" : "TEST-LOAN-USD",
  "currency" : "USD",
  "bookValue" : 991304.3478260869,
  "principalFlow" : 104347.82608695653,
  "interestIncome" : 4413.04347826087,
  "interestExpense" : 0.0,
  "couponIncome" : 0.0,
  "fxPnl" : 0.0,
  "npv" : 107835.08756347056,
  "nominalBalance" : 991304.3478260869
},
{...}
]
```
## Command Line Tool:

```
Usage: java -Dconfig=<config.yml> -jar cli.jar <entity> <command> [args]

API entities (require -Dconfig):
  deals      list                    List all deals
  deals      insert  file=<path>     Insert deals from JSON array file
  deals      truncate                Delete all deals
  markets    list                    List all market data
  markets    insert  file=<path>     Insert market data from JSON array file
  markets    truncate                Delete all market data
  cashflows  list                    List all cashflows
  cashflows  truncate                Delete all cashflows
  results    list                    List all results
  results    truncate                Delete all results
  process    start   file=<path>     Start async processing with JSON settings file
  process    status  id=<uuid>       Fetch one job's status
  process    list                    List all processing jobs (newest first)

File conversion (no -Dconfig needed):
  convert    jsonToCsv  file=<path>  Convert JSON array to CSV
  convert    csvToJson  file=<path>  Convert CSV to JSON array (types inferred)
  convert    jsonToXLS  file=<path>  Convert JSON array to XLSX (types set)
  convert    XLSToJson  file=<path>  Convert XLSX to JSON array

Config file (YAML):
  baseUrl: http://localhost:8082
  bearerToken: your-secret-token

Processing settings JSON example:
  {
    "processingStartDate": "2024-01-01",
    "processingEndDate":   "2025-12-31",
    "timeband": "Monthly",
    "reportingCurrency": "USD"
  }
```