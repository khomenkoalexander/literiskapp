-- LiteRisk application database schema
-- Runs on first PostgreSQL container start (docker-entrypoint-initdb.d)

CREATE SCHEMA IF NOT EXISTS literiskapp;

SET search_path TO literiskapp;

CREATE TABLE IF NOT EXISTS literiskapp.deal (
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
    amortization_type   VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS literiskapp.market (
    id      BIGSERIAL           PRIMARY KEY,
    type    VARCHAR(50),
    object  VARCHAR(100),
    date    DATE,
    value   DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS literiskapp.cashflow (
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

CREATE TABLE IF NOT EXISTS literiskapp.result (
    id               BIGSERIAL       PRIMARY KEY,
    deal             VARCHAR(100),
    interval_date    DATE,
    side             VARCHAR(20),
    currency         VARCHAR(10),
    bv               DOUBLE PRECISION,
    principal        DOUBLE PRECISION,
    interest_income  DOUBLE PRECISION
);
