package com.fintrack.networth;

import com.fintrack.common.entity.NetWorthEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NetWorthEventRepository extends JpaRepository<NetWorthEvent, UUID> {

    List<NetWorthEvent> findByUserIdOrderByEventDateDesc(UUID userId);

    Optional<NetWorthEvent> findByIdAndUserId(UUID id, UUID userId);
}
