package com.aivle.backend.audit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DomainAuditServiceTests {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final DomainAuditService service = new DomainAuditService(
        repository,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void acceptsPersonaInputIdentifiersWithoutOpeningTheAuditMetadataBoundary() {
        assertDoesNotThrow(() -> service.record(
            1L,
            2L,
            AuditEventType.PERSONA_RECOMMENDATION_REQUESTED,
            "AnalysisJob",
            3L,
            null,
            Map.of(
                "jobId", "3",
                "structuredPlanId", "4",
                "feasibilityAssessmentId", "5",
                "recommendationId", "6",
                "primaryPersonaCode", "KMP25-20-F-01"
            )
        ));

        verify(repository).save(org.mockito.ArgumentMatchers.any(AuditEvent.class));
    }

    @Test
    void stillRejectsUnknownMetadataKeys() {
        assertThrows(IllegalArgumentException.class, () -> service.record(
            1L,
            2L,
            AuditEventType.PERSONA_RECOMMENDATION_REQUESTED,
            "AnalysisJob",
            3L,
            null,
            Map.of("rawPrompt", "must not be logged")
        ));
    }
}
