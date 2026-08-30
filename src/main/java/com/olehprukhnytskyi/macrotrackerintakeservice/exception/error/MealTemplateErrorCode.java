package com.olehprukhnytskyi.macrotrackerintakeservice.exception.error;

import com.olehprukhnytskyi.exception.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum MealTemplateErrorCode implements BaseErrorCode {
    MEAL_TEMPLATE_LIMIT_REACHED(
            "Meal template limit reached", HttpStatus.TOO_MANY_REQUESTS.value());

    private final String title;
    private final int status;

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getStatus() {
        return status;
    }
}
