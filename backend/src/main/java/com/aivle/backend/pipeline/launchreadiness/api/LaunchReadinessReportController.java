package com.aivle.backend.pipeline.launchreadiness.api;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessReportBundleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/reports")
@RequiredArgsConstructor
public class LaunchReadinessReportController {
    private final CurrentUserProvider user;
    private final LaunchReadinessReportBundleService reports;
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long projectId, @RequestParam List<String> modules) {
        List<String> distinct = modules == null ? List.of() : modules.stream().distinct().toList();
        byte[] body = reports.create(user.currentUserId(), projectId, distinct);
        String filename = distinct.size() > 1 ? "launch-readiness-integrated-report.pdf" : distinct.get(0) + "-readiness-report.pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(new ByteArrayResource(body));
    }
}
