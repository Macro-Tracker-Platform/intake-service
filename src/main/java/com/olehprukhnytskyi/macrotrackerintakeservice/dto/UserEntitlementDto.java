package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEntitlementDto {
    private String plan;
    private Features features;

    public boolean hasPremiumAccess() {
        return "PRO".equalsIgnoreCase(plan);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Features {
        private boolean futurePlanning;
    }
}
