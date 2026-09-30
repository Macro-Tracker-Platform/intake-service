package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.client.UserEntitlementClient;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UserEntitlementDto;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanningEntitlementService {
    private final UserEntitlementClient client;

    public void requireFuturePlanning(Long userId) {
        UserEntitlementDto entitlement = client.getEntitlement(userId);
        if (entitlement == null || entitlement.getFeatures() == null
                || !entitlement.getFeatures().isFuturePlanning()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Future meal planning requires MacroTracker Pro");
        }
    }

    public boolean hasPremiumAccess(Long userId) {
        UserEntitlementDto entitlement = client.getEntitlement(userId);
        return entitlement != null && entitlement.hasPremiumAccess();
    }

    public void validatePlanningDate(Long userId, LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(14))) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Planned meals must be dated within the next 14 days");
        }
        if (date.isAfter(today.plusDays(1)) && !hasPremiumAccess(userId)) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Planning more than one day ahead requires MacroTracker Pro");
        }
    }
}
