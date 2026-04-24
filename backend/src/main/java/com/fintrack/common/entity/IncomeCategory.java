package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/** User-defined income categories. Pre-seeded with Turkish defaults. */
@Entity
@Table(name = "income_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String icon;

    @Column(length = 7)
    private String color;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;
}
