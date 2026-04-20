package com.literiskapp.api;

/**
 * Whether the deal represents an asset or a liability from the firm's perspective.
 * Drives the sign of interest income/expense in the result aggregation.
 * Stored as TEXT in the database.
 */
public enum AssetLiability {
    ASSET,
    LIABILITY
}
