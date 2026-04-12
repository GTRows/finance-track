package com.fintrack.bills;

import com.fintrack.common.entity.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    List<BillPayment> findByBillIdOrderByPeriodDesc(UUID billId);

    Optional<BillPayment> findByBillIdAndPeriod(UUID billId, String period);
}
