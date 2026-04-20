package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "market")
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    public MarketType type;

    @Column(name = "object")
    public String object;

    public LocalDate date;

    public String tenor;

    public Double dvalue;
}
