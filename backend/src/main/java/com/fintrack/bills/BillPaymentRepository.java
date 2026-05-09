package com.fintrack.bills;

import com.fintrack.common.entity.BillPayment;
import java.util.Collection;
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

    /**
     * Returns all payments for any of the given bills in the given period in a single query. Used
     * by {@code DashboardService.buildUpcomingBills} and {@code BillService.listForUser} to avoid
     * N+1 queries when listing multiple bills.
     */
    List<BillPayment> findByBillIdInAndPeriod(Collection<UUID> billIds, String period);

    /**
     * Returns all payments for any of the given bills filtered by status, ordered by period
     * descending. Used by {@code BillService.listForUser} to compute variance for many bills in a
     * single query (Java-side group-and-take-2 mirrors the per-bill {@code findTop2} semantics).
     */
    List<BillPayment> findByBillIdInAndStatusOrderByPeriodDesc(
            Collection<UUID> billIds, BillPayment.PaymentStatus status);
}
