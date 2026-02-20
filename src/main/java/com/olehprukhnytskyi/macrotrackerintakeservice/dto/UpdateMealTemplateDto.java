package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a reusable meal template")
public class UpdateMealTemplateDto {
    @Schema(description = "Name of the meal template", example = "Morning Porridge")
    private String name;

    @Valid
    @Size(min = 1, message = "Template must contain at least 1 item")
    @Schema(description = "List of food items included in this template")
    private List<TemplateItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual food item within a template")
    public static class TemplateItemDto {
        @NotBlank
        @Schema(description = "ID of the food from Food Service", example = "12345678")
        private String foodId;

        @Min(1)
        @Schema(description = "Standard amount in grams for this template", example = "100")
        private Integer amount;

        @Schema(description = "Available measurement units for the product", example = "GRAMS")
        private UnitType unitType;
    }
}
