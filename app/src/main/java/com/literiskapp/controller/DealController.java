package com.literiskapp.controller;

import com.literiskapp.api.Deal;
import com.literiskapp.repository.DealRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealRepository dealRepository;

    public DealController(DealRepository dealRepository) {
        this.dealRepository = dealRepository;
    }

    @GetMapping
    public List<Deal> listAll() {
        return dealRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Deal> insert(@RequestBody Deal deal) {
        Deal saved = dealRepository.save(deal);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping
    public ResponseEntity<Void> truncate() {
        dealRepository.truncate();
        return ResponseEntity.noContent().build();
    }
}
