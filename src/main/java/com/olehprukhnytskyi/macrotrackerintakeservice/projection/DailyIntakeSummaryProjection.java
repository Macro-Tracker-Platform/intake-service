package com.olehprukhnytskyi.macrotrackerintakeservice.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyIntakeSummaryProjection {
    LocalDate getDate();

    BigDecimal getCalories();

    BigDecimal getProtein();

    BigDecimal getFat();

    BigDecimal getCarbohydrates();
}
