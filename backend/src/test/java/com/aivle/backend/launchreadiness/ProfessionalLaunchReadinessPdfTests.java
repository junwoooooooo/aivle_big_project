package com.aivle.backend.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport;
import com.aivle.backend.launchreadiness.repository.ProfessionalAnalysisReportRepository;
import com.aivle.backend.launchreadiness.service.ProfessionalAnalysisAiClient;
import com.aivle.backend.launchreadiness.service.ProfessionalLaunchReadinessService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.lowagie.text.pdf.PdfReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProfessionalLaunchReadinessPdfTests {
    @Test
    void rendersStructuredTechnologyReportAsPdf() throws Exception {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProfessionalAnalysisReportRepository reports = mock(ProfessionalAnalysisReportRepository.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        String input = "{\"systemArchitecture\":\"웹·API·데이터베이스 3계층 구조\",\"testPlan\":\"부하 및 장애 복구 테스트\"}";
        String analysis = """
            {"score":78,"decision":"CONDITIONAL","summary":"핵심 구조와 테스트 계획은 작성되었으나 보안 통제와 장애 복구 증빙을 완료한 뒤 조건부 출시 여부를 다시 확인해야 합니다.",
            "missing":["데이터·보안 기준"],"completed":["시스템·제품 구조","테스트·검증 계획"],
            "dimensions":[{"name":"아키텍처","score":82,"status":"READY","finding":"웹·API·데이터베이스 계층이 분리되어 변경 영향 범위를 관리할 수 있습니다."},{"name":"보안·데이터","score":58,"status":"RISK","finding":"권한과 백업 기준이 입력되지 않아 출시 전 통제 증빙이 필요합니다."},{"name":"성능·확장","score":72,"status":"CAUTION","finding":"목표 처리량과 측정 환경을 테스트 계획에 추가해야 합니다."},{"name":"테스트·출시","score":80,"status":"READY","finding":"부하 및 장애 복구 테스트 범위가 명시되어 있습니다."}],
            "risks":[{"title":"권한 통제 미확정","severity":"HIGH","likelihood":"MEDIUM","impact":"운영자 권한 오남용과 개인정보 접근 추적 누락이 발생할 수 있습니다.","mitigation":"역할별 권한표와 접근 로그 보존 기준을 승인합니다."},{"title":"복구 목표 미확정","severity":"MEDIUM","likelihood":"MEDIUM","impact":"장애 시 복구 우선순위가 달라질 수 있습니다.","mitigation":"복구 시간과 데이터 손실 허용 기준을 테스트합니다."},{"title":"용량 기준 미확정","severity":"LOW","likelihood":"LOW","impact":"수요 증가 시 성능 저하를 늦게 발견할 수 있습니다.","mitigation":"부하 단계별 경보 기준을 기록합니다."}],
            "gates":[{"title":"접근권한 승인","status":"OPEN","criterion":"역할별 최소 권한과 로그 추적을 테스트해 승인합니다.","evidenceNeeded":"권한표와 접근 로그"},{"title":"장애 복구","status":"OPEN","criterion":"복구 훈련을 수행하고 목표 시간 내 복구를 확인합니다.","evidenceNeeded":"복구 테스트 로그"},{"title":"부하 테스트","status":"PASS","criterion":"목표 동시 사용자 조건에서 응답 기준을 통과합니다.","evidenceNeeded":"부하 테스트 결과"},{"title":"배포 승인","status":"OPEN","criterion":"릴리스 체크리스트와 롤백 절차를 담당자가 승인합니다.","evidenceNeeded":"승인 이력"}],
            "actions":[{"priority":"P0","title":"권한 통제 확정","owner":"보안 담당","completionEvidence":"승인된 권한표"},{"priority":"P1","title":"복구 훈련","owner":"인프라 담당","completionEvidence":"복구 테스트 로그"},{"priority":"P2","title":"용량 경보 설정","owner":"개발 담당","completionEvidence":"경보 설정 화면"}],
            "externalEvidence":[{"title":"OWASP Application Security Verification Standard","url":"https://owasp.org/www-project-application-security-verification-standard/"}],
            "quality":{"passed":true,"reviewScore":92,"attempts":2,"feedback":[],"unsupportedClaims":[]}}
            """;
        ProfessionalAnalysisReport report = ProfessionalAnalysisReport.create("report-1", 41L,
            ProfessionalAnalysisReport.ModuleType.TECHNOLOGY, input, analysis, 7L, Instant.parse("2026-08-14T00:00:00Z"));
        when(reports.findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(
            41L, ProfessionalAnalysisReport.ModuleType.TECHNOLOGY)).thenReturn(Optional.of(report));
        ProfessionalLaunchReadinessService service = new ProfessionalLaunchReadinessService(
            projects, reports, new ObjectMapper(), mock(ProfessionalAnalysisAiClient.class));

        byte[] output = service.pdf(7L, 41L, ProfessionalAnalysisReport.ModuleType.TECHNOLOGY);
        Path qa = Path.of("build", "qa", "technology-analysis-sample.pdf");
        Files.createDirectories(qa.getParent()); Files.write(qa, output);

        PdfReader reader = new PdfReader(output);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();
    }
}
