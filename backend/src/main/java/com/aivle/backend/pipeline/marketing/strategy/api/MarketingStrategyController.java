package com.aivle.backend.pipeline.marketing.strategy.api;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyPdfService;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    "/api/v3/projects/{projectId}/marketing-strategy"
)
@RequiredArgsConstructor
public class MarketingStrategyController {

    private final MarketingStrategyService strategies;
    private final MarketingStrategyPdfService pdfs;
    private final CurrentUserProvider users;

    @GetMapping
    public ApiResponse<
        MarketingStrategyApiModels.StrategyView
    > current(
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(
            strategies.current(
                users.currentUserId(),
                projectId
            ),
            request.getHeader("X-Request-Id")
        );
    }

    @PostMapping("/generate")
    public ResponseEntity<
        ApiResponse<
            MarketingStrategyApiModels
                .StrategyActionResponse
        >
    > generate(
        @PathVariable Long projectId,
        @RequestHeader("Idempotency-Key")
            String idempotencyKey,
        HttpServletRequest request
    ) {
        var result = strategies.start(
            users.currentUserId(),
            projectId,
            idempotencyKey,
            request.getHeader("X-Correlation-Id")
        );

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(
                ApiResponse.success(
                    result,
                    request.getHeader("X-Request-Id")
                )
            );
    }

    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
        @PathVariable Long projectId,
        @PathVariable String reportId
    ) {
        var report = strategies.requireCurrent(
            users.currentUserId(),
            projectId,
            reportId
        );

        byte[] pdf = pdfs.render(report);

        ContentDisposition disposition =
            ContentDisposition.attachment()
                .filename(
                    "marketing-strategy-"
                        + projectId
                        + ".pdf",
                    StandardCharsets.UTF_8
                )
                .build();

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                disposition.toString()
            )
            .contentLength(pdf.length)
            .body(pdf);
    }
}
