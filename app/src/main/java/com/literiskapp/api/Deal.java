package com.literiskapp.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "deal")
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

    public String repricingFreq;

    // --- Security (bond) fields ---
    public Double faceValue;
    public Double couponRate;
    public String couponFreq;
    public LocalDate couponStart;
    public String discountCurve;
    public String marketPriceObj;

    // --- FX swap fields ---
    public String fxNearCurrency;
    public String fxFarCurrency;
    public Double fxNearAmount;
    public Double fxFarAmount;
    public LocalDate fxNearDate;
    public LocalDate fxFarDate;
    public Double fxSpotRate;
    public Double fxForwardRate;
}
