package com.aivle.backend.validation.market;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.validation.market.MarketResponseScoringService.MessageVariant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/market-responses")
@RequiredArgsConstructor
public class MarketResponseController {
    private final MarketResponseService service;
    private final CurrentUserProvider currentUser;

    @GetMapping
    ApiResponse<List<MarketResponseService.SummaryResponse>> list(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.list(currentUser.currentUserId(), projectId),
            requestId(request)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MarketResponseService.DetailResponse> create(
        @PathVariable Long projectId,
        @Valid @RequestBody Request body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.create(currentUser.currentUserId(), projectId, body.command(), requestId(request)),
            requestId(request)
        );
    }

    @GetMapping("/{predictionId}")
    ApiResponse<MarketResponseService.DetailResponse> detail(
        @PathVariable Long projectId,
        @PathVariable Long predictionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.detail(currentUser.currentUserId(), projectId, predictionId),
            requestId(request)
        );
    }

    @PatchMapping("/{predictionId}")
    ApiResponse<MarketResponseService.DetailResponse> update(
        @PathVariable Long projectId,
        @PathVariable Long predictionId,
        @Valid @RequestBody Request body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.update(currentUser.currentUserId(), projectId, predictionId, body.command(), requestId(request)),
            requestId(request)
        );
    }

    @PostMapping("/{predictionId}/run")
    ApiResponse<MarketResponseService.DetailResponse> run(
        @PathVariable Long projectId,
        @PathVariable Long predictionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            service.run(currentUser.currentUserId(), projectId, predictionId, requestId(request)),
            requestId(request)
        );
    }

    @DeleteMapping("/{predictionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
        @PathVariable Long projectId,
        @PathVariable Long predictionId,
        HttpServletRequest request
    ) {
        service.delete(currentUser.currentUserId(), projectId, predictionId, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    record Request(
        @NotBlank @Size(max = 200) String title,
        @NotNull List<@NotNull Long> personaIds,
        @NotNull List<@NotNull MessageRequest> messages,
        @Size(max = 300) String priceContext,
        @Size(max = 80) String primaryChannel,
        Long panelInterviewId
    ) {
        MarketResponseService.Command command() {
            return new MarketResponseService.Command(
                title,
                personaIds,
                messages.stream().map(value -> new MessageVariant(value.id(), value.text())).toList(),
                priceContext,
                primaryChannel,
                panelInterviewId
            );
        }
    }

    record MessageRequest(
        @NotBlank @Size(max = 10) String id,
        @NotBlank @Size(max = 300) String text
    ) { }
}
