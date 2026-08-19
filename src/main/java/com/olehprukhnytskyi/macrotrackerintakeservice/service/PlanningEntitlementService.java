package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.client.UserEntitlementClient;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UserEntitlementDto;
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
}
