package com.literiskapp.api;

/**
 * Principal amortisation schedule type.
 * Stored as TEXT in the database.
 */
public enum AmortizationType {
    /** Equal principal instalments over the life of the deal. */
    LINEAR,
    /** Full principal returned at maturity (no intermediate repayments). */
    BULLET
}
