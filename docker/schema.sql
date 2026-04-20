-- LiteRisk application database schema
-- Runs on first PostgreSQL container start (docker-entrypoint-initdb.d)

CREATE TABLE IF NOT EXISTS deal (
    id                  VARCHAR(100)        PRIMARY KEY,
    type                VARCHAR(50),
    deal_date           DATE,
    maturity_date       DATE,
    original_principal  DOUBLE PRECISION,
    currency            VARCHAR(10),
    asset_liability     VARCHAR(20),
    nominal_rate        DOUBLE PRECISION,
    book_value          DOUBLE PRECISION,
    int_pay_freq        VARCHAR(20),
    int_pay_start       DATE,
    prin_pay_freq       VARCHAR(20),
    prin_pay_start      DATE,
    amortization_type   VARCHAR(50),
    repricing_freq      VARCHAR(20),
    -- security (bond) fields
    face_value          DOUBLE PRECISION,
    coupon_rate         DOUBLE PRECISION,
    coupon_freq         VARCHAR(20),
    coupon_start        DATE,
    discount_curve      VARCHAR(100),
    market_price_obj    VARCHAR(100),
    -- fx swap fields
    fx_near_currency    VARCHAR(10),
    fx_far_currency     VARCHAR(10),
    fx_near_amount      DOUBLE PRECISION,
    fx_far_amount       DOUBLE PRECISION,
    fx_near_date        DATE,
    fx_far_date         DATE,
    fx_spot_rate        DOUBLE PRECISION,
    fx_forward_rate     DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS market (
    id      BIGSERIAL           PRIMARY KEY,
    type    VARCHAR(50),
    object  VARCHAR(100),
    date    DATE,
    tenor   VARCHAR(20),
    dvalue   DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS cashflow (
    id                   BIGSERIAL           PRIMARY KEY,
    deal                 VARCHAR(100),
    type                 VARCHAR(50),
    date                 DATE,
    amount               DOUBLE PRECISION,
    currency             VARCHAR(10),
    remaining_principal  DOUBLE PRECISION,
    accrued_interest     DOUBLE PRECISION,
    book_value           DOUBLE PRECISION,
    nominal_rate         DOUBLE PRECISION,
    discount_factor      DOUBLE PRECISION,
    npv                  DOUBLE PRECISION
);

-- OLAP fact table: one row per (asset_liability, interval_date, deal, currency)
-- Measures are expressed in reporting currency.
CREATE TABLE IF NOT EXISTS result (
    id                BIGSERIAL         PRIMARY KEY,
    asset_liability   VARCHAR(20),
    interval_date     DATE,
    deal              VARCHAR(100),
    currency          VARCHAR(10),
    book_value        DOUBLE PRECISION,
    principal_flow    DOUBLE PRECISION,
    interest_income   DOUBLE PRECISION,
    interest_expense  DOUBLE PRECISION,
    coupon_income     DOUBLE PRECISION,
    fx_pnl            DOUBLE PRECISION,
    npv               DOUBLE PRECISION,
    nominal_balance   DOUBLE PRECISION,
    CONSTRAINT result_unique UNIQUE (asset_liability, interval_date, deal, currency)
);

-- Async processing job tracking
CREATE TABLE IF NOT EXISTS processing_status (
    id                    UUID              PRIMARY KEY,
    status                VARCHAR(20),
    requested_at          TIMESTAMP,
    started_at            TIMESTAMP,
    finished_at           TIMESTAMP,
    settings_json         TEXT,
    error_message         TEXT,
    cashflows_generated   BIGINT,
    results_generated     BIGINT
);
