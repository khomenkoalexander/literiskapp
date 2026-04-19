package com.literiskapp.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "deal", schema = "literiskapp")
public class Deal {

    @Id
    public String id;

    public String type;

    public LocalDate dealDate;

    public LocalDate maturityDate;

    public Double originalPrincipal;

    public String currency;

    public String assetLiability;

    public Double nominalRate;

    public Double bookValue;

    public String intPayFreq;

    public LocalDate intPayStart;

    public String prinPayFreq;

    public LocalDate prinPayStart;

    public String amortizationType;
}
