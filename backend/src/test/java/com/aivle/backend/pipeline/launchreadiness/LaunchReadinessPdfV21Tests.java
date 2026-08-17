package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.ProfessionalAnalysisView;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessDocumentService;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessPdfService;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessService;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessInputSnapshotRepository;
import com.lowagie.text.pdf.PdfReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessPdfV21Tests {
    @Test
    void rendersStructuredKoreanProfessionalReportFromCurrentResultAndInputEvidence() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LaunchReadinessService readiness = mock(LaunchReadinessService.class);
        LaunchReadinessInputSnapshotRepository snapshots = mock(LaunchReadinessInputSnapshotRepository.class);
        var analysis = mapper.readTree("""
            {"decision":"CONDITIONAL","score":78,"summary":"보안 통제 증빙을 완료하면 출시를 진행할 수 있습니다.",
             "dimensions":[
               {"name":"아키텍처","score":82,"status":"READY","finding":"계층이 분리되어 있습니다."},
               {"name":"보안·데이터","score":58,"status":"RISK","finding":"권한 증빙이 필요합니다."},
               {"name":"성능·확장","score":72,"status":"CAUTION","finding":"부하 기준을 보완해야 합니다."},
               {"name":"테스트·출시","score":80,"status":"READY","finding":"복구 테스트가 정의되어 있습니다."}],
             "risks":[
               {"title":"권한 통제","severity":"HIGH","impact":"접근 추적 누락","mitigation":"권한표 승인"},
               {"title":"복구 목표","severity":"MEDIUM","impact":"복구 지연","mitigation":"복구 훈련"},
               {"title":"용량 기준","severity":"LOW","impact":"성능 저하","mitigation":"경보 설정"}],
             "gates":[
               {"title":"접근권한","status":"OPEN","criterion":"권한 테스트","evidenceNeeded":"권한표"},
               {"title":"복구","status":"OPEN","criterion":"복구 훈련","evidenceNeeded":"복구 로그"},
               {"title":"부하","status":"PASS","criterion":"목표 처리량","evidenceNeeded":"부하 결과"},
               {"title":"배포","status":"OPEN","criterion":"승인 완료","evidenceNeeded":"승인 이력"}],
             "actions":[
               {"priority":"P0","title":"권한 통제 확정","owner":"보안 담당","completionEvidence":"권한표"},
               {"priority":"P1","title":"복구 훈련","owner":"인프라 담당","completionEvidence":"복구 로그"},
               {"priority":"P2","title":"경보 설정","owner":"개발 담당","completionEvidence":"경보 화면"}],
             "externalEvidence":[{"title":"OWASP ASVS","url":"https://owasp.org/www-project-application-security-verification-standard/"}],
             "quality":{"passed":true,"reviewScore":92,"attempts":2,"feedback":[],"unsupportedClaims":[]}}
            """);
        var quality = analysis.path("quality");
        var evidence = analysis.path("externalEvidence");
        Instant completedAt = Instant.parse("2026-08-14T00:00:00Z");
        when(readiness.current(7L, 41L, ModuleType.TECHNOLOGY)).thenReturn(new ProfessionalAnalysisView(
            "TECHNOLOGY", "SUCCEEDED", false, null, "run-1", "run-1", "snapshot-1", "technology.docx",
            mapper.createObjectNode().put("systemArchitecture", "웹·API·데이터베이스 3계층 구조"),
            hash('a'), hash('b'), "report-1", hash('c'), analysis, quality, evidence, completedAt, true, false,
            false, null, "CURRENT_CONCEPT_AND_PROFESSIONAL_INPUT",
            mapper.createObjectNode().put("marketSeedSnapshotId", "seed-1")
                .put("selectionId", 11L).put("selectionRevision", 3).put("bmPlanRevision", 4)));
        var snapshot = LaunchReadinessInputSnapshot.create("snapshot-1", 41L, ModuleType.TECHNOLOGY,
            "artifact-1", hash('a'), "technology.docx",
            "{\"systemArchitecture\":\"웹·API·데이터베이스 3계층 구조\",\"testPlan\":\"부하 및 장애 복구 테스트\"}",
            hash('b'), 7L, completedAt);
        when(snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, ModuleType.TECHNOLOGY)).thenReturn(Optional.of(snapshot));

        byte[] output = new LaunchReadinessPdfService(readiness, snapshots,
            new LaunchReadinessDocumentService(), mapper).create(7L, 41L, ModuleType.TECHNOLOGY, true);

        Files.createDirectories(Path.of("build", "qa"));
        Files.write(Path.of("build", "qa", "v21-technology-readiness.pdf"), output);
        assertThat(output).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        PdfReader reader = new PdfReader(output);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
