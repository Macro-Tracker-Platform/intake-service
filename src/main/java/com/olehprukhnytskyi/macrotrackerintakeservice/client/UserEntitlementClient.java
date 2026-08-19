package com.olehprukhnytskyi.macrotrackerintakeservice.client;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UserEntitlementDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${feign.user-service:http://localhost:8082}")
public interface UserEntitlementClient {
    @GetMapping("/api/users/me/entitlements")
    UserEntitlementDto getEntitlement(@RequestHeader(CustomHeaders.X_USER_ID) Long userId);
}
