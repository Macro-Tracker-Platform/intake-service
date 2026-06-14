package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to log a consumed cooked recipe portion")
public class RecipeIntakeRequestDto {
    @NotNull
    @Min(1)
    @Schema(description = "Consumed cooked recipe amount", example = "2")
    private Integer consumedAmount;

    @NotNull
    @Schema(description = "Unit used to measure the consumed recipe portion", example = "PIECES")
    private UnitType unitType;
}
