package com.fintrack.alert;

import com.fintrack.common.entity.AlertNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertNotificationRepository extends JpaRepository<AlertNotification, UUID> {

    List<AlertNotification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<AlertNotification> findByIdAndUserId(UUID id, UUID userId);
}
