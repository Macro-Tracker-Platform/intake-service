package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealTemplateItemDto {
    private String foodId;
    private String foodName;
    private int amount;
    private UnitType unitType;
    private NutrimentsDto nutriments;

    @Schema(
            description = "Available measurement units for the product",
            example = "[\"GRAMS\", \"PIECES\"]"
    )
    private List<UnitType> availableUnits;
}
