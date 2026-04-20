package com.literiskapp.api;

/**
 * Market data record type — determines the lookup bucket inside
 * {@link com.literiskapp.processing.MarketDataService}.
 * Stored as TEXT in the database.
 */
public enum MarketType {
    /** Foreign-exchange spot/forward rate. Object format: "CCY1/CCY2". */
    FX,
    /** Discount / zero / yield curve. Object is the curve name. */
    CURVE,
    /** Market price (e.g. bond clean price, % of face). */
    PRICE,
    /** Short-term interest rate benchmark. */
    INTEREST_RATE
}
