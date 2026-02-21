package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.BigDecimalJsonSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nutrition values")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NutrimentsDto {
    @Schema(description = "Total calories", example = "247.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal calories;

    @Schema(description = "Total carbohydrates", example = "0.0", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydrates;

    @Schema(description = "Total fat", example = "5.4", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fat;

    @Schema(description = "Total protein", example = "46.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal protein;

    @Schema(description = "Calories per piece", example = "120.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal caloriesPerPiece;

    @Schema(description = "Carbohydrates per piece", example = "53.9", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydratesPerPiece;

    @Schema(description = "Fat per piece", example = "2.1", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fatPerPiece;

    @Schema(description = "Protein per piece", example = "22.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal proteinPerPiece;

    @Schema(description = "Calories per 100g", example = "120.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal caloriesPer100;

    @Schema(description = "Carbohydrates per 100g", example = "53.9", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal carbohydratesPer100;

    @Schema(description = "Fat per 100g", example = "2.1", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal fatPer100;

    @Schema(description = "Protein per 100g", example = "22.5", minimum = "0.0")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    private BigDecimal proteinPer100;
}
