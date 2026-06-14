package com.olehprukhnytskyi.macrotrackerintakeservice.model;

import com.olehprukhnytskyi.util.UnitType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import java.util.List;
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
public class MealTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID requestId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "is_recipe", nullable = false)
    private boolean recipe = false;

    private Integer totalYieldAmount;

    @Enumerated(EnumType.STRING)
    private UnitType yieldUnitType;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "item_position")
    private List<MealTemplateItem> items;
}
