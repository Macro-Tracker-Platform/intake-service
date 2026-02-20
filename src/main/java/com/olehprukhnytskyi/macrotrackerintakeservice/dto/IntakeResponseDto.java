package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food intake response with calculated nutrition")
public class IntakeResponseDto {
    @Schema(description = "Intake record ID", example = "12345")
    private Long id;

    @Schema(
            description = "ID grouping multiple foods consumed in one meal (e.g., from a template)",
            example = "987fc3-a1b2-44"
    )
    private String mealGroupId;

    @Schema(
            description = "Food product ID",
            example = "507f1f77bcf86cd799439011"
    )
    private String foodId;

    @Schema(description = "Food name", example = "Chicken Breast")
    private String foodName;

    @Schema(description = "Consumed amount in grams", example = "150")
    private int amount;

    @Schema(description = "Available measurement units for the product", example = "GRAMS")
    private UnitType unitType;

    @Schema(description = "Consumption date", example = "2024-01-15")
    private LocalDate date;

    @Schema(description = "Consumption period", example = "BREAKFAST")
    private IntakePeriod intakePeriod;

    @Schema(description = "Calculated nutrition values for consumed amount")
    @Builder.Default
    private NutrimentsDto nutriments = new NutrimentsDto();

    @Builder.Default
    private List<UnitType> availableUnits = new ArrayList<>();
}
