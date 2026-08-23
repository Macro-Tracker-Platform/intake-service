package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEntitlementDto {
    private Features features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Features {
        private boolean futurePlanning;
    }
}
