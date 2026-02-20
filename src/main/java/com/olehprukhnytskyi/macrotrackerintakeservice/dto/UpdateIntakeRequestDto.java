package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food intake update request")
public class UpdateIntakeRequestDto {
    @Schema(
            description = "Updated consumed amount in grams",
            example = "200",
            minimum = "1"
    )
    @Min(1)
    private Integer amount;

    @Schema(
            description = "Updated intake date (user's local date, yyyy-MM-dd)",
            example = "2024-01-15"
    )
    private LocalDate date;

    @Schema(
            description = "Updated meal period",
            example = "DINNER",
            allowableValues = {
                    "BREAKFAST",
                    "LUNCH",
                    "DINNER",
                    "SNACK"
            }
    )
    private IntakePeriod intakePeriod;

    private UnitType unitType;
}
