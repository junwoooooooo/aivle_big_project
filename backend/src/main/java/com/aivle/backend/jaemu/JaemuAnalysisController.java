package com.aivle.backend.jaemu;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jaemu")
public class JaemuAnalysisController {
    private final JaemuAnalysisService service;
    public JaemuAnalysisController(JaemuAnalysisService service) { this.service = service; }
    @PostMapping("/analysis")
    public ResponseEntity<JaemuAnalysisResponse> analyze(@Valid @RequestBody JaemuAnalysisRequest request) {
        return ResponseEntity.ok(service.analyze(request));
    }

    @PostMapping("/pipeline")
    public ResponseEntity<JaemuPipelineResponse> pipeline(@Valid @RequestBody JaemuPipelineRequest request) {
        return ResponseEntity.ok(service.pipeline(request));
    }
}
