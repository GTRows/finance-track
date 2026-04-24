package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/** System-wide admin configuration stored in the database. */
@Entity
@Table(name = "admin_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSetting {

    @Id
    @Column(nullable = false, length = 100)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(columnDefinition = "TEXT")
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
