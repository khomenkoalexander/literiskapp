package com.literiskapp.repository;

import com.literiskapp.api.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface MarketRepository extends JpaRepository<Market, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Market m")
    void truncate();
}
