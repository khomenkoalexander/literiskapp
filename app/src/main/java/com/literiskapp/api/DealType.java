package com.literiskapp.api;

/**
 * Discriminator for the deal processing engine. Determines which
 * {@link com.literiskapp.processing.CashflowGenerator} is used.
 * Stored as TEXT in the database.
 */
public enum DealType {
    REGULAR_PAYMENT,
    CASH_ACCOUNT,
    FX_SWAP,
    SECURITY
}
