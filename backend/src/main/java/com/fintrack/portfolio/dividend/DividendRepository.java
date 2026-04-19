package com.fintrack.portfolio.dividend;

import com.fintrack.common.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, UUID> {

    List<Dividend> findByPortfolioIdOrderByPaymentDateDesc(UUID portfolioId);

    List<Dividend> findByAssetIdOrderByPaymentDateDesc(UUID assetId);

    Optional<Dividend> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    @Query("SELECT COALESCE(SUM(d.netAmountTry), 0) FROM Dividend d " +
           "WHERE d.portfolioId = :portfolioId AND d.assetId = :assetId")
    BigDecimal sumNetByPortfolioAndAsset(UUID portfolioId, UUID assetId);

    @Query("SELECT COALESCE(SUM(d.netAmountTry), 0) FROM Dividend d " +
           "WHERE d.portfolioId IN :portfolioIds AND d.paymentDate BETWEEN :from AND :to")
    BigDecimal sumNetByPortfoliosAndRange(List<UUID> portfolioIds, LocalDate from, LocalDate to);
}
