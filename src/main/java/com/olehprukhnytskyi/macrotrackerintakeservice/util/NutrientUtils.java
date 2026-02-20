package com.olehprukhnytskyi.macrotrackerintakeservice.util;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.model.NutrientAware;
import com.olehprukhnytskyi.util.UnitType;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NutrientUtils {
    public static void validateUnitType(UnitType unitType, NutrientAware nutrientData) {
        if (unitType == null || nutrientData == null) {
            return;
        }
        Set<UnitType> availableUnits = nutrientData.getAvailableUnits();
        if (!availableUnits.contains(unitType)) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST, String.format(
                    "Unit type '%s' is not available for this food. Available units: %s",
                    unitType, availableUnits
            ));
        }
    }

    public static void validateUnitSupported(FoodDto food, UnitType requestedUnit) {
        if (food.getAvailableUnits() == null
                || !food.getAvailableUnits().contains(requestedUnit)) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    String.format(
                            "Food '%s' does not support unit type %s. Available types: %s",
                            food.getProductName(),
                            requestedUnit,
                            food.getAvailableUnits()));
        }
    }
}
