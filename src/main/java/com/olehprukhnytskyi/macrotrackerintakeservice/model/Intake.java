package com.olehprukhnytskyi.macrotrackerintakeservice.model;

import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Intake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID requestId;

    private String mealGroupId;

    private String mealTemplateName;

    private Integer mealItemPosition;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String foodId;

    @Builder.Default
    @Embedded
    private Nutriments nutriments = new Nutriments();

    private String foodName;

    private String brand;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer amount;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UnitType unitType = UnitType.GRAMS;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IntakePeriod intakePeriod = IntakePeriod.SNACK;

    private String originalFoodId;

    private String moderationStatus;

    @Builder.Default
    private boolean verifiedByAdmin = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
