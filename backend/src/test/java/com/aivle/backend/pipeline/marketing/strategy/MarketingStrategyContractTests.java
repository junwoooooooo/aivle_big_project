package com.aivle.backend.pipeline.marketing.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyResultContract;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyService;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategySourceService;
import com.aivle.backend.pipeline.marketing.strategy.repository.MarketingStrategyReportRepository;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class MarketingStrategyContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentConceptAloneIsReadyAndOptionalAnalysisRemainsOptional() {
        FinalReportService finalReports = mock(FinalReportService.class);
        var manifestSources = mapper.createArrayNode();
        manifestSources.addObject().put("type", "PROJECT").put("id", "7");
        manifestSources.addObject().put("type", "CURRENT_CONCEPT").put("id", "concept-1");
        ObjectNode sourceData = mapper.createObjectNode();
        sourceData.putObject("CURRENT_CONCEPT").put("conceptName", "자전거 운영 분석");
        when(finalReports.currentSourceCatalog(11L, 7L)).thenReturn(
            new FinalReportService.CurrentSourceCatalog(manifestSources, sourceData,
                java.util.Map.of("CURRENT_CONCEPT", "AVAILABLE"), List.of(), List.of(),
                "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64)));

        var service = new MarketingStrategySourceService(
            finalReports, mapper);
        var bundle = service.inspect(11L, 7L);

        assertThat(bundle.ready()).isTrue();
        assertThat(bundle.sources().has("CURRENT_CONCEPT")).isTrue();
        assertThat(bundle.hash()).isEqualTo("sha256:" + "b".repeat(64));
        assertThat(bundle.missing()).contains("MARKET", "BUSINESS_MODEL", "MARKET_INTERVIEW");
    }

    @Test
    void resultContractKeepsCampaignBudgetChannelAndEvidenceFields() {
        var result = mapper.createObjectNode();
        result.put("contract", "marketing-strategy-result-v1");
        result.put("executiveSummary", "현재 사업안 기준 전략");
        result.putArray("targetCustomers").add("지자체 운영 담당자");
        result.put("positioning", "운영 데이터를 실행 정보로 전환");
        result.putArray("coreMessages").add("운영 판단을 빠르게");
        result.putArray("contentPillars").add("운영 효율");
        var channel = result.putArray("channelStrategies").addObject();
        channel.put("channel", "공공 제안"); channel.put("objective", "검토 기회 확보");
        channel.put("audience", "담당자"); channel.putArray("actions").add("사례 제시");
        channel.putArray("kpis").add("검토 수"); channel.put("rationale", "구매 절차에 맞음");
        var phase = result.putArray("campaignRoadmap").addObject();
        phase.put("phase", "1단계"); phase.put("objective", "메시지 확인");
        phase.putArray("actions").add("사례 정리"); phase.putArray("kpis").add("검토 완료");
        result.putArray("budgetGuidelines").add("확인된 범위 안에서 배분");
        result.putArray("risks").add("성과 단정 금지");
        result.putArray("evidenceRefs").add("CURRENT_CONCEPT:concept-1");

        new MarketingStrategyResultContract().validate(result);
    }

    @Test
    void startAndCurrentUseTheCurrentConceptWithoutOptionalHardGates() {
        var sources = mock(MarketingStrategySourceService.class);
        var reports = mock(MarketingStrategyReportRepository.class);
        var taskRuns = mock(TaskRunService.class);
        var runRepository = mock(TaskRunRepository.class);
        var hasher = mock(CanonicalInputHasher.class);
        var events = mock(JobEventPublisher.class);
        var manifest = mapper.createArrayNode();
        manifest.addObject().put("type", "CURRENT_CONCEPT").put("id", "concept-1");
        var sourceData = mapper.createObjectNode();
        sourceData.putObject("CURRENT_CONCEPT").put("conceptName", "자전거 운영 분석");
        var bundle = new MarketingStrategySourceService.SourceBundle(
            manifest, sourceData, "sha256:" + "b".repeat(64), List.of("MARKET", "FINANCE"), true);
        when(sources.inspect(11L, 7L)).thenReturn(bundle);
        when(hasher.hash(org.mockito.ArgumentMatchers.eq(TaskType.MARKETING_STRATEGY_GENERATION),
            org.mockito.ArgumentMatchers.eq("1.0"), org.mockito.ArgumentMatchers.eq("ko-KR"),
            org.mockito.ArgumentMatchers.anyString())).thenReturn("sha256:" + "c".repeat(64));
        TaskRun task = mock(TaskRun.class);
        when(task.getId()).thenReturn("strategy-task-1");
        when(task.getState()).thenReturn(TaskRunState.QUEUED);
        when(taskRuns.createWithDisposition(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(new TaskRunService.CreateResult(task, true, false));
        when(reports.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(7L))
            .thenReturn(Optional.empty());
        when(runRepository.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            7L, TaskType.MARKETING_STRATEGY_GENERATION)).thenReturn(Optional.empty());

        var service = new MarketingStrategyService(sources, reports, new MarketingStrategyResultContract(),
            taskRuns, runRepository, hasher, events, mapper);
        var started = service.start(11L, 7L, "strategy-key", "correlation-1");
        var current = service.current(11L, 7L);

        assertThat(started.taskRunId()).isEqualTo("strategy-task-1");
        assertThat(current.status()).isEqualTo("NOT_STARTED");
        assertThat(current.ready()).isTrue();
        assertThat(current.missingSources()).contains("MARKET", "FINANCE");
        verify(taskRuns).createWithDisposition(org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(TaskType.MARKETING_STRATEGY_GENERATION),
            org.mockito.ArgumentMatchers.eq("MARKETING_STRATEGY"),
            org.mockito.ArgumentMatchers.eq("b".repeat(64)),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("strategy-key"),
            org.mockito.ArgumentMatchers.eq("correlation-1"), org.mockito.ArgumentMatchers.eq(2));
    }
}
