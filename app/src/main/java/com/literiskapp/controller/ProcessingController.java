package com.literiskapp.controller;

import com.literiskapp.api.ProcessingSettings;
import com.literiskapp.api.ProcessingStatus;
import com.literiskapp.service.ProcessingService;
import com.literiskapp.service.ProcessingService.JobBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/process")
public class ProcessingController {

    private final ProcessingService service;

    public ProcessingController(ProcessingService service) {
        this.service = service;
    }

    /** Submit a new processing job. 202 Accepted with status row, 409 if busy. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody ProcessingSettings settings) {
        try {
            ProcessingStatus s = service.submit(settings);
            return ResponseEntity.accepted().body(s);
        } catch (JobBusyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessingStatus> get(@PathVariable UUID id) {
        ProcessingStatus s = service.get(id);
        return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
    }

    @GetMapping
    public List<ProcessingStatus> list() {
        return service.list();
    }
}
