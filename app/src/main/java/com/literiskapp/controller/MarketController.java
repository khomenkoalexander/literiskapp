package com.literiskapp.controller;

import com.literiskapp.api.Market;
import com.literiskapp.repository.MarketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/markets")
public class MarketController {

    private final MarketRepository marketRepository;

    public MarketController(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @GetMapping
    public List<Market> listAll() {
        return marketRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<List<Market>> insert(@RequestBody List<Market> markets) {
        markets.forEach(m -> m.id = null); // ensure ids are generated
        List<Market> saved = marketRepository.saveAll(markets);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping
    public ResponseEntity<Void> truncate() {
        marketRepository.truncate();
        return ResponseEntity.noContent().build();
    }
}
