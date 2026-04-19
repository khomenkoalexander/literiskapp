package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "result", schema = "literiskapp")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String deal;

    @Column(name = "interval_date")
    public LocalDate interval;

    public String side;

    public String currency;

    public Double bv;

    public Double principal;

    public Double interestIncome;
}
