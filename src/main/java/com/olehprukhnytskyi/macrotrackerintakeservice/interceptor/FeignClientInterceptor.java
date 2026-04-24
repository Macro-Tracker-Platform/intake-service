package com.olehprukhnytskyi.macrotrackerintakeservice.interceptor;

import com.olehprukhnytskyi.util.CustomHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader(CustomHeaders.X_USER_ID);
            if (userId != null) {
                requestTemplate.header(CustomHeaders.X_USER_ID, userId);
            }
        }
    }
}
