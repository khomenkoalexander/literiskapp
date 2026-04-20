package com.literiskapp.api;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processing_status")
public class ProcessingStatus {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    public ProcessingState status;

    public LocalDateTime requestedAt;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    public String settingsJson;

    @Column(columnDefinition = "TEXT")
    public String errorMessage;

    public Long cashflowsGenerated;
    public Long resultsGenerated;
}
