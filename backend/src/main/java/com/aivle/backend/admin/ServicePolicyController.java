package com.aivle.backend.admin;

import com.aivle.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-policy")
@RequiredArgsConstructor
public class ServicePolicyController {
    private final ServicePolicyService servicePolicy;

    @GetMapping
    public ApiResponse<ServicePolicyResponse> getServicePolicy(HttpServletRequest request) {
        return ApiResponse.success(
            ServicePolicyResponse.from(servicePolicy.snapshot()),
            request.getHeader("X-Request-Id")
        );
    }
}
