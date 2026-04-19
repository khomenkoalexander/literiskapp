package com.literiskapp.repository;

import com.literiskapp.api.Cashflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface CashflowRepository extends JpaRepository<Cashflow, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Cashflow c")
    void truncate();
}
