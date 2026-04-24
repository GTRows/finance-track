package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Per-user preferences (1:1 with users table). */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "TRY";

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String language = "tr";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String theme = "dark";

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Europe/Istanbul";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dashboard_layout", columnDefinition = "jsonb")
    private Map<String, Object> dashboardLayout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", columnDefinition = "jsonb")
    private Map<String, Object> notificationPreferences;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private boolean onboardingCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
