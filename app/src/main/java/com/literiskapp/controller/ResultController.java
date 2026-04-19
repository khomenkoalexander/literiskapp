package com.literiskapp.controller;

import com.literiskapp.api.Result;
import com.literiskapp.repository.ResultRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/results")
public class ResultController {

    private final ResultRepository resultRepository;

    public ResultController(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    @GetMapping
    public List<Result> listAll() {
        return resultRepository.findAll();
    }

    @DeleteMapping
    public ResponseEntity<Void> truncate() {
        resultRepository.truncate();
        return ResponseEntity.noContent().build();
    }
}
