package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.olehprukhnytskyi.util.UnitType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealTemplateResponseDto {
    private Long id;
    private String name;
    @JsonProperty("isRecipe")
    private boolean recipe;
    private Integer totalYieldAmount;
    private UnitType yieldUnitType;
    private List<MealTemplateItemDto> items;
}
