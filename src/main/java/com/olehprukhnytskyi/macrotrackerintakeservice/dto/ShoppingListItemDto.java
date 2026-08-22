package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingListItemDto {
    private String foodId;
    private String foodName;
    private int amount;
    private UnitType unitType;
    private Long recipeId;
    private String recipeName;
}
