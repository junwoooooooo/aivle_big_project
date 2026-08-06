package com.aivle.backend.job.web;

import com.aivle.backend.job.runner.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e")
@ConditionalOnProperty(
    prefix = "app.jobs.document-processing",
    name = "enabled",
    havingValue = "true"
)
@RestController
@RequestMapping("/internal/e2e/jobs")
@RequiredArgsConstructor
public class E2eJobControlController {
    private final JobRunner jobRunner;

    @PostMapping("/{jobId}/wake")
    public ResponseEntity<Void> wake(@PathVariable Long jobId) {
        jobRunner.wake(jobId);
        return ResponseEntity.accepted().build();
    }
}
