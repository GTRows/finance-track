package com.fintrack.bills;

import com.fintrack.common.entity.BillPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    List<BillPayment> findByBillIdOrderByPeriodDesc(UUID billId);

    List<BillPayment> findTop2ByBillIdAndStatusOrderByPeriodDesc(
            UUID billId, BillPayment.PaymentStatus status);

    Optional<BillPayment> findByBillIdAndPeriod(UUID billId, String period);
}
