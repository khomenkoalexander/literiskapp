package com.literiskapp.repository;

import com.literiskapp.api.ProcessingState;
import com.literiskapp.api.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProcessingStatusRepository extends JpaRepository<ProcessingStatus, UUID> {

    List<ProcessingStatus> findByStatusIn(List<ProcessingState> states);

    List<ProcessingStatus> findAllByOrderByRequestedAtDesc();
}
