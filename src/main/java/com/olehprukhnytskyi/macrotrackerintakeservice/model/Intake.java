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
import java.time.LocalDate;
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

    private String mealGroupId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String foodId;

    @Builder.Default
    @Embedded
    private Nutriments nutriments = new Nutriments();

    private String foodName;

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
}
