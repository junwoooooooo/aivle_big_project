package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.EstimateDecisionRequest;
import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.finance.repository.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchService;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FinancialServiceAsyncTests {
    @Test
    void initializeBindsCurrentMarketAndBusinessModelWithoutTechOps() {
        Harness h = new Harness();
        when(h.preparations.findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            41L, 101L, 201L)).thenReturn(Optional.empty());
        when(h.preparations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = h.service.initialize(7L, 41L);

        assertThat(result.sourceTechOpsSnapshotId()).isNull();
        assertThat(result.sourceMarketResearchVersionId()).isEqualTo(101L);
        assertThat(result.sourceBusinessModelVersionId()).isEqualTo(201L);
        verifyNoInteractions(h.techOpsSnapshots);
    }

    @Test
    void initializeIsProviderFreeAndCreatesNoEstimateTask() {
        Harness h = new Harness();
        when(h.preparations.findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(41L, 101L, 201L))
            .thenReturn(Optional.empty());
        when(h.preparations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = h.service.initialize(7L, 41L);

        assertThat(result.assistance().path("totalMarketingCost").path("estimateStatus").asText()).isEqualTo("NONE");
        verifyNoInteractions(h.taskRuns, h.events);
    }

    @Test
    void initializeBlocksWhenCurrentMarketIsMissing() {
        Harness h = new Harness();
        when(h.marketResearch.current(7L, 41L, MarketResearchRun.Kind.FULL))
            .thenReturn(new MarketResearchService.CurrentView(null, null, null, false));
        assertPreparationRequired(() -> h.service.initialize(7L, 41L), "Market Research");
    }

    @Test
    void initializeBlocksWhenCurrentBusinessModelIsMissing() {
        Harness h = new Harness();
        when(h.marketResearch.current(7L, 41L, MarketResearchRun.Kind.BM))
            .thenReturn(new MarketResearchService.CurrentView(null, null, null, false));
        assertPreparationRequired(() -> h.service.initialize(7L, 41L), "Business Model");
    }

    @Test
    void initializeBlocksAStaleMarket() {
        Harness h = new Harness();
        when(h.marketResearch.current(7L, 41L, MarketResearchRun.Kind.FULL))
            .thenReturn(new MarketResearchService.CurrentView(null, h.marketView, null, true));
        assertPreparationRequired(() -> h.service.initialize(7L, 41L), "Market Research");
    }

    @Test
    void initializeBlocksAStaleBusinessModel() {
        Harness h = new Harness();
        when(h.marketResearch.current(7L, 41L, MarketResearchRun.Kind.BM))
            .thenReturn(new MarketResearchService.CurrentView(null, h.bmView, null, true));
        assertPreparationRequired(() -> h.service.initialize(7L, 41L), "Business Model");
    }

    @Test
    void initializeBlocksAMarketBusinessModelLineageMismatch() {
        Harness h = new Harness();
        when(h.bmRun.getSourceMarketVersionId()).thenReturn(999L);
        assertPreparationRequired(() -> h.service.initialize(7L, 41L), "lineage");
    }

    @Test
    void acceptAndEditUseRequiredProvenanceWithoutProviderCall() {
        Harness accepted = new Harness();
        accepted.installPreparation(hMoney(12000000));
        var accept = accepted.service.decideEstimate(7L, 41L, "annualFixedRentAndManagementCost",
            new EstimateDecisionRequest("ACCEPT", null), null, null);
        JsonNode acceptedField = accept.preparation().financialFields().path("annualFixedRentAndManagementCost");
        assertThat(acceptedField.path("source").asText()).isEqualTo("AI_ESTIMATE");
        assertThat(acceptedField.path("decision").asText()).isEqualTo("ACCEPTED");

        Harness edited = new Harness();
        edited.installPreparation(hMoney(12000000));
        var edit = edited.service.decideEstimate(7L, 41L, "annualFixedRentAndManagementCost",
            new EstimateDecisionRequest("EDIT_AND_ACCEPT", hMoney(9000000)), null, null);
        JsonNode editedField = edit.preparation().financialFields().path("annualFixedRentAndManagementCost");
        assertThat(editedField.path("source").asText()).isEqualTo("USER_INPUT");
        assertThat(editedField.path("decision").asText()).isEqualTo("USER_EDITED_ACCEPTED");
        verifyNoInteractions(accepted.taskRuns, edited.taskRuns);
    }

    @Test
    void alternativeQueuesVersionTwoAndRetainsCurrentProposal() {
        Harness h = new Harness();
        h.installPreparation(hMoney(12000000));

        var result = h.service.decideEstimate(7L, 41L, "annualFixedRentAndManagementCost",
            new EstimateDecisionRequest("REQUEST_ALTERNATIVE", null), "command-2", "request-2");

        JsonNode proposal = result.preparation().assistance().path("annualFixedRentAndManagementCost");
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.proposalVersion()).isEqualTo(2);
        assertThat(proposal.path("proposalValue").path("amount").asInt()).isEqualTo(12000000);
        assertThat(proposal.path("activeTaskRunId").asText()).isEqualTo("task-1");
    }

    @Test
    void rejectPreservesProposalAsAuditEvidenceWithoutChangingFinancialField() {
        Harness h = new Harness();
        h.installPreparation(hMoney(12000000));
        var result = h.service.decideEstimate(7L, 41L, "annualFixedRentAndManagementCost",
            new EstimateDecisionRequest("REJECT", null), null, null);
        assertThat(result.preparation().assistance().path("annualFixedRentAndManagementCost")
            .path("decision").asText()).isEqualTo("REJECTED");
        assertThat(result.preparation().financialFields().path("annualFixedRentAndManagementCost")
            .path("value").isNull()).isTrue();
        verifyNoInteractions(h.taskRuns);
    }

    private static JsonNode hMoney(int amount) {
        return new ObjectMapper().readTree("{\"amount\":" + amount + ",\"currency\":\"KRW\"}");
    }

    private static void assertPreparationRequired(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String messageFragment) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FINANCIAL_PREPARATION_REQUIRED);
            assertThat(error.getMessage()).contains(messageFragment);
        });
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final String hash = "sha256:" + "a".repeat(64);
        final ProjectRepository projects = mock(ProjectRepository.class);
        final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
        final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        final MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        final TechOpsInputSnapshotRepository techOpsSnapshots = mock(TechOpsInputSnapshotRepository.class);
        final MarketResearchService marketResearch = mock(MarketResearchService.class);
        final MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
        final FinancialInputPreparationRepository preparations = mock(FinancialInputPreparationRepository.class);
        final FinancialInputSnapshotRepository snapshots = mock(FinancialInputSnapshotRepository.class);
        final FinancialPreparationFactory factory = new FinancialPreparationFactory(mapper);
        final FinancialInputSnapshotFactory snapshotFactory = mock(FinancialInputSnapshotFactory.class);
        final FinancialReadiness readiness = mock(FinancialReadiness.class);
        final FinancialCalculator calculator = new FinancialCalculator(mapper);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        final SnapshotHasher snapshotHasher = mock(SnapshotHasher.class);
        final JobEventPublisher events = mock(JobEventPublisher.class);
        final MarketResearchRun marketRun = mock(MarketResearchRun.class);
        final MarketResearchRun bmRun = mock(MarketResearchRun.class);
        final MarketResearchService.VersionView marketView = new MarketResearchService.VersionView(
            101L, "FULL", 1, mapper.createObjectNode(), 0, 0, null, null, 0, 0, 0);
        final MarketResearchService.VersionView bmView = new MarketResearchService.VersionView(
            201L, "BM", 1, mapper.createObjectNode(), 0, 0, "GO", "HIGH", null, null, null);
        final FinancialService service = new FinancialService(projects, marketSeeds,
            marketResearch, marketVersions, preparations, snapshots, factory, snapshotFactory,
            readiness, calculator, mapper, taskRuns, hasher, snapshotHasher, events);
        final TechOpsInputSnapshot source;

        Harness() {
            Project project = mock(Project.class); User owner = mock(User.class);
            when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
            when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
            ConceptSelection selection = mock(ConceptSelection.class); when(selection.getId()).thenReturn(9L);
            when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L))
                .thenReturn(Optional.of(selection));
            MarketAnalysisSeedSnapshot seed = MarketAnalysisSeedSnapshot.create("seed-1", 41L, 9L, "concept-1",
                "2.0", hash, hash, "{\"finalHypotheses\":{}}", 7L, Instant.EPOCH);
            when(marketSeeds.findById("seed-1")).thenReturn(Optional.of(seed));
            source = TechOpsInputSnapshot.create("tech-1", 41L, "tech-prep-1", "seed-1", "2.0", hash,
                "{\"requiredFacts\":{},\"requiredFactProvenance\":{}}", 7L, Instant.EPOCH);
            when(techOpsSnapshots.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull("seed-1", 41L))
                .thenReturn(Optional.of(source));
            when(marketResearch.current(7L, 41L, MarketResearchRun.Kind.FULL))
                .thenReturn(new MarketResearchService.CurrentView(null, marketView, null, false));
            when(marketResearch.current(7L, 41L, MarketResearchRun.Kind.BM))
                .thenReturn(new MarketResearchService.CurrentView(null, bmView, null, false));
            MarketResearchVersion market = mock(MarketResearchVersion.class);
            MarketResearchVersion bm = mock(MarketResearchVersion.class);
            Project sourceProject = mock(Project.class); when(sourceProject.getId()).thenReturn(41L);
            when(market.getId()).thenReturn(101L); when(market.getProject()).thenReturn(sourceProject);
            when(market.getKind()).thenReturn(MarketResearchRun.Kind.FULL); when(market.getResultJson()).thenReturn("{}");
            when(market.getSourceRun()).thenReturn(marketRun); when(marketRun.getSourceMarketSeedSnapshotId()).thenReturn("seed-1");
            when(bm.getId()).thenReturn(201L); when(bm.getProject()).thenReturn(sourceProject);
            when(bm.getKind()).thenReturn(MarketResearchRun.Kind.BM); when(bm.getResultJson()).thenReturn("{}");
            when(bm.getSourceRun()).thenReturn(bmRun); when(bmRun.getSourceMarketVersionId()).thenReturn(101L);
            when(marketVersions.findById(101L)).thenReturn(Optional.of(market));
            when(marketVersions.findById(201L)).thenReturn(Optional.of(bm));
            when(snapshotHasher.hash(any())).thenReturn(hash);
            when(snapshots.findByPreparationIdAndProjectIdAndDeletedAtIsNull(anyString(), eq(41L)))
                .thenReturn(Optional.empty());
            when(readiness.missing(any())).thenReturn(List.of("annualFixedRentAndManagementCost"));
            when(hasher.hash(eq(TaskType.FINANCE_ESTIMATE), eq("1.0"), eq("ko-KR"), anyString()))
                .thenReturn("sha256:" + "b".repeat(64));
            TaskRun task = mock(TaskRun.class);
            when(task.getId()).thenReturn("task-1"); when(task.getState()).thenReturn(TaskRunState.QUEUED);
            when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.FINANCE_ESTIMATE),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(1)))
                .thenReturn(new TaskRunService.CreateResult(task, true, false));
        }

        void installPreparation(JsonNode proposedValue) {
            ObjectNode fields = mapper.createObjectNode();
            FinancialPreparationFactory.ALL_KEYS.forEach(key -> {
                ObjectNode field = fields.putObject(key); field.putNull("value");
                field.put("readOnly", false); field.put("source", "USER_INPUT"); field.put("decision", "OPEN");
            });
            ObjectNode assistance = mapper.createObjectNode();
            ObjectNode proposal = assistance.putObject("annualFixedRentAndManagementCost");
            proposal.set("proposalValue", proposedValue.deepCopy()); proposal.put("proposalVersion", 1);
            proposal.put("source", "AI_ESTIMATE"); proposal.put("decision", "PROPOSED");
            proposal.put("estimateStatus", "SUCCEEDED"); proposal.putNull("activeTaskRunId");
            FinancialInputPreparation preparation = FinancialInputPreparation.create("prep-1", 41L, "tech-1", "seed-1",
                101L, 201L, hash, mapper.writeValueAsString(fields), "{}", mapper.writeValueAsString(assistance), 7L);
            when(preparations.findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(41L, 101L, 201L))
                .thenReturn(Optional.of(preparation));
            when(preparations.findLocked("prep-1", 41L)).thenReturn(Optional.of(preparation));
        }
    }
}
