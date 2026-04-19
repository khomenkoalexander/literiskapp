package com.literiskapp.controller;

import com.literiskapp.api.Cashflow;
import com.literiskapp.repository.CashflowRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashflows")
public class CashflowController {

    private final CashflowRepository cashflowRepository;

    public CashflowController(CashflowRepository cashflowRepository) {
        this.cashflowRepository = cashflowRepository;
    }

    @GetMapping
    public List<Cashflow> listAll() {
        return cashflowRepository.findAll();
    }

    @DeleteMapping
    public ResponseEntity<Void> truncate() {
        cashflowRepository.truncate();
        return ResponseEntity.noContent().build();
    }
}
