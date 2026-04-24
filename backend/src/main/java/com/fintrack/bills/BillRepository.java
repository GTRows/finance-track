package com.fintrack.bills;

import com.fintrack.common.entity.Bill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    List<Bill> findByUserIdOrderByDueDayAsc(UUID userId);

    List<Bill> findByUserIdAndActiveTrueOrderByDueDayAsc(UUID userId);

    Optional<Bill> findByIdAndUserId(UUID id, UUID userId);
}
