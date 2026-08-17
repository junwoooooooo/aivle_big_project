package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FinancialUserDocumentAuthorityV21Tests {
    @Test
    void importsUserDocumentWithoutMarketOrBusinessModelAndKeepsItsValuesAuthoritative() {
        ObjectMapper mapper = new ObjectMapper();
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = mock(Project.class, RETURNS_DEEP_STUBS);
        when(project.getOwner().getId()).thenReturn(7L);
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        MarketResearchService marketResearch = mock(MarketResearchService.class);
        MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
        FinancialInputPreparationRepository preparations = mock(FinancialInputPreparationRepository.class);
        FinancialInputSnapshotRepository snapshots = mock(FinancialInputSnapshotRepository.class);
        when(preparations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FinancialCalculator calculator = new FinancialCalculator(mapper);
        SnapshotHasher hasher = new SnapshotHasher(mapper);
        CurrentConceptSourceResolver currentConcepts = mock(CurrentConceptSourceResolver.class);
        FinancialService service = new FinancialService(projects, currentConcepts, marketSeeds, marketResearch, marketVersions,
            preparations, snapshots, new FinancialPreparationFactory(mapper),
            new FinancialInputSnapshotFactory(mapper, hasher, calculator), new FinancialReadiness(), calculator,
            mapper, mock(TaskRunService.class), mock(CanonicalInputHasher.class), hasher,
            mock(JobEventPublisher.class));

        ObjectNode values = completeValues(mapper, 987_654_321);
        values.putObject(FinancialInputDocumentService.INPUT_NOTES)
            .put("annualFixedLaborCost", "개발자 2명, 초기 3~4인 기준");
        var result = service.importUserDocument(7L, 41L, "artifact-1", hash('d'), values);

        assertThat(result.sourceMarketResearchVersionId()).isNull();
        assertThat(result.sourceBusinessModelVersionId()).isNull();
        assertThat(result.snapshot().path("sourceMode").asText()).isEqualTo("USER_DOCUMENT_INPUT");
        assertThat(result.snapshot().path("sourceDocumentArtifactId").asText()).isEqualTo("artifact-1");
        assertThat(result.snapshot().path("values").path("annualFixedLaborCost").path("amount").asLong())
            .isEqualTo(987_654_321L);
        assertThat(result.snapshot().path("valueProvenance").path("annualFixedLaborCost")
            .path("userNote").asText()).isEqualTo("개발자 2명, 초기 3~4인 기준");
        assertThat(result.snapshot().path("upstreamReferences").path("userDocument")
            .path("normalizedValues").path(FinancialInputDocumentService.INPUT_NOTES)
            .path("annualFixedLaborCost").asText()).isEqualTo("개발자 2명, 초기 3~4인 기준");
        verifyNoInteractions(marketSeeds, marketResearch, marketVersions);
        verifyNoInteractions(currentConcepts);
    }

    private ObjectNode completeValues(ObjectMapper mapper, long laborCost) {
        ObjectNode values = mapper.createObjectNode();
        for (String key : FinancialPreparationFactory.ALL_KEYS) {
            if ("revenueModel".equals(key)) values.put(key, "SUBSCRIPTION");
            else if ("newCustomerCount".equals(key)) values.put(key, 1500);
            else if ("monthlyChurnRate".equals(key)) values.put(key, 3.5);
            else if ("threeYearTargets".equals(key)) {
                ObjectNode target = values.putObject(key); target.put("metric", "customerCount"); target.put("unit", "명");
                var years = target.putArray("years");
                years.addObject().put("year", 1).put("value", 100);
                years.addObject().put("year", 2).put("value", 200);
                years.addObject().put("year", 3).put("value", 400);
            } else {
                ObjectNode money = values.putObject(key);
                money.put("amount", "annualFixedLaborCost".equals(key) ? laborCost : 10_000);
                money.put("currency", "KRW");
            }
        }
        return values;
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
