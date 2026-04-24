package com.fintrack.debt;

import com.fintrack.common.entity.DebtPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    List<DebtPayment> findByDebtIdOrderByPaymentDateAsc(UUID debtId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM DebtPayment p WHERE p.debtId = :debtId")
    BigDecimal sumByDebtId(@Param("debtId") UUID debtId);
}
