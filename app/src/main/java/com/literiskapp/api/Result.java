package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * OLAP fact row: unique per (assetLiability, interval, deal, currency).
 * All numeric measures are expressed in the reporting currency.
 */
@Entity
@Table(
    name = "result",
    uniqueConstraints = @UniqueConstraint(
        name = "result_unique",
        columnNames = {"asset_liability", "interval_date", "deal", "currency"}
    )
)
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "asset_liability")
    public String assetLiability;

    @Column(name = "interval_date")
    public LocalDate interval;

    public String deal;

    /** Original deal currency (traceability); measures below are in reporting currency. */
    public String currency;

    public Double bookValue;
    public Double principalFlow;
    public Double interestIncome;
    public Double interestExpense;
    public Double couponIncome;
    public Double fxPnl;
    public Double npv;
    public Double nominalBalance;
}
