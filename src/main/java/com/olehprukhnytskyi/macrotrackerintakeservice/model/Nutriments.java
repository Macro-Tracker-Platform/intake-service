package com.olehprukhnytskyi.macrotrackerintakeservice.model;

import com.olehprukhnytskyi.model.NutrientAware;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Nutriments implements NutrientAware {
    @Column(name = "calories_per_100")
    private BigDecimal caloriesPer100;

    @Column(name = "carbohydrates_per_100")
    private BigDecimal carbohydratesPer100;

    @Column(name = "fat_per_100")
    private BigDecimal fatPer100;

    @Column(name = "protein_per_100")
    private BigDecimal proteinPer100;

    @Column(name = "calories_per_piece")
    private BigDecimal caloriesPerPiece;

    @Column(name = "carbohydrates_per_piece")
    private BigDecimal carbohydratesPerPiece;

    @Column(name = "fat_per_piece")
    private BigDecimal fatPerPiece;

    @Column(name = "protein_per_piece")
    private BigDecimal proteinPerPiece;

    @Builder.Default
    @Column(name = "calories_total", nullable = false, columnDefinition = "decimal default 0")
    private BigDecimal calories = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "carbohydrates_total", nullable = false, columnDefinition = "decimal default 0")
    private BigDecimal carbohydrates = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "fat_total", nullable = false, columnDefinition = "decimal default 0")
    private BigDecimal fat = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "protein_total", nullable = false, columnDefinition = "decimal default 0")
    private BigDecimal protein = BigDecimal.ZERO;

    @Override
    public boolean isGramsDataComplete() {
        return caloriesPer100 != null && proteinPer100 != null
               && fatPer100 != null && carbohydratesPer100 != null;
    }

    @Override
    public boolean isPiecesDataComplete() {
        return caloriesPerPiece != null && proteinPerPiece != null
               && fatPerPiece != null && carbohydratesPerPiece != null;
    }
}
