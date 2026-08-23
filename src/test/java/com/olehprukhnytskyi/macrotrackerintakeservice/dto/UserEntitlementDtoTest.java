package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UserEntitlementDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ignoresUnknownFeatureFieldsFromUserServiceEntitlement() throws Exception {
        String json = """
                {
                  "plan": "PRO",
                  "state": "PRO_ACTIVE",
                  "validUntil": "2026-01-01T00:00:00Z",
                  "legacyAccess": false,
                  "features": {
                    "nutritionLabelScans": {
                      "limit": 30,
                      "remaining": 29,
                      "resetAt": "2026-01-01T00:00:00Z"
                    },
                    "advancedInsights": true,
                    "futurePlanning": true,
                    "weekdayGoals": true,
                    "adaptiveCalories": true
                  }
                }
                """;

        UserEntitlementDto entitlement = objectMapper.readValue(json, UserEntitlementDto.class);

        assertThat(entitlement.getFeatures().isFuturePlanning()).isTrue();
    }
}
