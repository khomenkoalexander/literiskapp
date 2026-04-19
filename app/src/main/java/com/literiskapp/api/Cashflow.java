package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "cashflow", schema = "literiskapp")
public class Cashflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String deal;

    public String type;

    public LocalDate date;

    public Double amount;

    public String currency;

    public Double remainingPrincipal;

    public Double accruedInterest;

    public Double bookValue;

    public Double nominalRate;

    public Double discountFactor;

    public Double npv;
}
