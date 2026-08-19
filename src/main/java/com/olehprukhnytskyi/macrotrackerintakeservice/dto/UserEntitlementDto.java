package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import lombok.Data;

@Data
public class UserEntitlementDto {
    private Features features;

    @Data
    public static class Features {
        private boolean futurePlanning;
    }
}
