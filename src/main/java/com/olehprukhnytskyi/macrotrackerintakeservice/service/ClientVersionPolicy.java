package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientVersionPolicy {
    public static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";

    private final int planningMinVersionCode;

    public ClientVersionPolicy(
            @Value("${app.compatibility.planning-min-version-code:46}")
            int planningMinVersionCode) {
        this.planningMinVersionCode = planningMinVersionCode;
    }

    public boolean supportsPlanning(String appVersionCodeHeader) {
        if (appVersionCodeHeader == null || appVersionCodeHeader.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(appVersionCodeHeader.trim()) >= planningMinVersionCode;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
