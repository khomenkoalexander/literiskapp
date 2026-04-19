package com.literiskapp.repository;

import com.literiskapp.api.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DealRepository extends JpaRepository<Deal, String> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Deal d")
    void truncate();
}
