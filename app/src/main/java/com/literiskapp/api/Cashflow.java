package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "cashflow")
public class Cashflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String deal;

    /** INTEREST, PRINCIPAL, COUPON, FX_NEAR, FX_FAR, MATURITY */
    public String type;

    /** Actual cashflow date (not bucketed). */
    public LocalDate date;

    /** Amount in cashflow currency (may differ from deal currency for FX legs). */
    public Double amount;

    public String currency;

    public Double remainingPrincipal;

    public Double accruedInterest;

    public Double bookValue;

    public Double nominalRate;

    public Double discountFactor;

    /** NPV in cashflow currency. */
    public Double npv;
}
