package com.fintrack.alert;

import com.fintrack.common.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

    @Query("SELECT a FROM PriceAlert a JOIN FETCH a.asset WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    List<PriceAlert> findAllByUserId(UUID userId);

    @Query("SELECT a FROM PriceAlert a JOIN FETCH a.asset WHERE a.status = com.fintrack.common.entity.PriceAlert.Status.ACTIVE")
    List<PriceAlert> findAllActiveWithAsset();

    Optional<PriceAlert> findByIdAndUserId(UUID id, UUID userId);
}
