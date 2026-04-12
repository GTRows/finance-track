package com.fintrack.bills;

import com.fintrack.common.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    List<Bill> findByUserIdOrderByDueDayAsc(UUID userId);

    List<Bill> findByUserIdAndActiveTrueOrderByDueDayAsc(UUID userId);

    Optional<Bill> findByIdAndUserId(UUID id, UUID userId);
}
