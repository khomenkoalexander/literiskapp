package com.literiskapp.repository;

import com.literiskapp.api.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ResultRepository extends JpaRepository<Result, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Result r")
    void truncate();
}
