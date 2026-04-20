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

    @Enumerated(EnumType.STRING)
    public CashflowType type;

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
