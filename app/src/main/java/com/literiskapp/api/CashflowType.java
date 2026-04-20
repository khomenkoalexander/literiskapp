package com.literiskapp.api;

/**
 * Cashflow event type — determines how the cashflow is bucketed into
 * {@link Result} measures by the aggregator.
 * Stored as TEXT in the database.
 */
public enum CashflowType {
    /** Periodic interest payment on a loan or deposit. */
    INTEREST,
    /** Intermediate principal repayment (amortising loan). */
    PRINCIPAL,
    /** Bond coupon payment. */
    COUPON,
    /** Final principal / face value returned at maturity. */
    MATURITY,
    /** Near-date exchange leg of an FX swap. */
    FX_NEAR,
    /** Far-date exchange leg of an FX swap. */
    FX_FAR,
    /** Mark-to-market P&amp;L on an FX swap far leg. */
    FX_PNL
}
