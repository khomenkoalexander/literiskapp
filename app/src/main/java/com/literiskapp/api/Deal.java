package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "deal")
public class Deal {

    @Id
    public String id;

    @Enumerated(EnumType.STRING)
    public DealType type;

    public LocalDate dealDate;

    public LocalDate maturityDate;

    public Double originalPrincipal;

    public String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_liability")
    public AssetLiability assetLiability;

    public Double nominalRate;

    public Double bookValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "int_pay_freq")
    public PaymentFrequency intPayFreq;

    public LocalDate intPayStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "prin_pay_freq")
    public PaymentFrequency prinPayFreq;

    public LocalDate prinPayStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "amortization_type")
    public AmortizationType amortizationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "repricing_freq")
    public PaymentFrequency repricingFreq;

    // --- Security (bond) fields ---
    public Double faceValue;
    public Double couponRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_freq")
    public PaymentFrequency couponFreq;

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
