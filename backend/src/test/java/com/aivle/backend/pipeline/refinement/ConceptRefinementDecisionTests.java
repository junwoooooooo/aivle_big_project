package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementDecisionTests {
    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final Long PROJECT_ID = 41L;
    private static final Long OWNER_ID = 7L;

    @Mock ProjectRepository projects;
    @Mock BusinessValidationCoordinator validations;
    @Mock ConceptPortfolioSelectionRepository selections;
    @Mock MarketAnalysisSeedSnapshotRepository seeds;
    @Mock BmPlanPreparationService bmPlans;
    @Mock ConceptRefinementRoundRepository rounds;
    @Mock ConceptRefinementService refinement;
    @Mock ConceptPortfolioSelectionService selectionCommands;
    @Mock ConceptPortfolioSelectionTaskFactory taskFactory;
    @Mock Project project;
    @Mock User owner;
    @Mock ConceptPortfolioSelection selection;

    private final ObjectMapper mapper = new ObjectMapper();
    private ConceptRefinementDecisionContract contract;
    private ConceptRefinementDecisionService service;
    private CompletedSource source;
    private MarketAnalysisSeedSnapshot seed;
    private ConceptRefinementRound round;

    @BeforeEach
    void setUp() {
        contract = new ConceptRefinementDecisionContract(mapper,
            new ConceptPortfolioJsonHasher(mapper));
        service = new ConceptRefinementDecisionService(projects, validations, selections, seeds,
            bmPlans, rounds, contract, refinement);
        source = new CompletedSource("session-1", 91L, 92L, "seed-1", 31L, 4, 3, HASH);
        seed = MarketAnalysisSeedSnapshot.createPortfolio("seed-1", PROJECT_ID, 31L,
            "concept-1", "legal-1", "2.0", HASH, HASH, "{}", OWNER_ID, Instant.parse("2026-08-16T00:00:00Z"));

        lenient().when(project.getOwner()).thenReturn(owner);
        lenient().when(owner.getId()).thenReturn(OWNER_ID);
        lenient().when(projects.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        lenient().when(validations.requireCurrentCompletedSource(OWNER_ID, PROJECT_ID)).thenReturn(source);
        lenient().when(selection.getId()).thenReturn(31L);
        lenient().when(selection.getHypothesisRevision()).thenReturn(4);
        lenient().when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(PROJECT_ID))
            .thenReturn(Optional.of(selection));
        lenient().when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-1"))
            .thenReturn(Optional.of(seed));
        lenient().when(bmPlans.current(PROJECT_ID)).thenReturn(new BmPlanPreparationService.PlanView(
            mapper.createObjectNode(), mapper.createObjectNode(), 3));
        round = awaiting(proposals(
            proposal("price", "10,000원", "12,500원", "MARKET", "E-1"),
            proposal("keyActivities", List.of("기존 활동"), List.of("신규 활동"), "BM", "BM-1"),
            proposal("targetUsers", "초기 고객", "확장 고객", "LEGAL", "L-1")));
        lenient().when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(round));
    }

    @Test
    void validSelectionRecordsAuthoritativeSnapshotAndThreeWayPlanWithoutProductMutation() {
        var set = contract.proposalSet(round);
        List<String> selected = List.of(key(set, "price"), key(set, "keyActivities"),
            key(set, "targetUsers"));

        service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1, set.hash(), selected, false);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.DECISION_RECORDED);
        JsonNode decision = mapper.readTree(round.getDecisionJson());
        JsonNode price = find(decision.path("selectedProposals"), "price");
        assertThat(price.path("currentValue").asText()).isEqualTo("10,000원");
        assertThat(price.path("proposedValue").asText()).isEqualTo("12,500원");
        assertThat(price.path("source").asText()).isEqualTo("MARKET");
        assertThat(price.path("evidenceIds").get(0).asText()).isEqualTo("E-1");
        assertThat(decision.at("/plan/hypotheses/PRICE").asText()).isEqualTo("12,500원");
        assertThat(decision.at("/plan/bmPlan/key_activities").get(0).asText()).isEqualTo("신규 활동");
        assertThat(decision.at("/plan/overlay/targetUsers").asText()).isEqualTo("확장 고객");
        assertThat(round.getDecisionHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(selection.getHypothesisRevision()).isEqualTo(4);
        assertThat(bmPlans.current(PROJECT_ID).revision()).isEqualTo(3);
        assertThat(seed.getStaleAt()).isNull();
        verify(bmPlans, never()).save(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(selectionCommands, taskFactory);
    }

    @Test
    void proposalProjectionHasStableKeysAndCanonicalSetHash() {
        var first = contract.proposalSet(round);
        var second = contract.proposalSet(round);

        assertThat(first.hash()).isEqualTo(second.hash()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.projected()).allSatisfy(value ->
            assertThat(value.path("proposalKey").asText()).matches("sha256:[0-9a-f]{64}"));
    }

    @Test
    void unknownProposalKeyAndOldProposalSetRejectEntireDecision() {
        var set = contract.proposalSet(round);
        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1,
            set.hash(), List.of("sha256:" + "f".repeat(64)), false))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-2", 1,
            "sha256:" + "b".repeat(64), List.of(key(set, "price")), false))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_VERSION_CONFLICT));
        assertThat(round.getDecisionJson()).isNull();
    }

    @Test
    void duplicateKeysAndTwoProposalsForSameFieldAreRejected() {
        var set = contract.proposalSet(round);
        String priceKey = key(set, "price");
        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1,
            set.hash(), List.of(priceKey, priceKey), false))
            .isInstanceOf(BusinessException.class);

        round = awaiting(proposals(
            proposal("price", "10,000원", "11,000원", "MARKET", "E-1"),
            proposal("price", "10,000원", "12,000원", "MARKET", "E-2")));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(round));
        var twoPrices = contract.proposalSet(round);
        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-2", 1,
            twoPrices.hash(), twoPrices.orderedKeys(), false))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(round.getDecisionJson()).isNull();
    }

    @Test
    void staleSourceMarksRoundStaleAndStoresNoDecision() {
        var set = contract.proposalSet(round);
        when(validations.requireCurrentCompletedSource(OWNER_ID, PROJECT_ID))
            .thenThrow(new BusinessException(ErrorCode.MODULE_INPUT_STALE));

        service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1, set.hash(),
            List.of(key(set, "price")), false);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.STALE);
        assertThat(round.getDecisionJson()).isNull();
        verify(bmPlans, never()).save(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(taskFactory);
    }

    @Test
    void keepCurrentStoresAllProposalsAsDeclinedAndRejectsMixedChoice() {
        var set = contract.proposalSet(round);
        service.decide(OWNER_ID, PROJECT_ID, "keep-1", 1, set.hash(), List.of(), true);

        assertThat(round.getState()).isEqualTo(ConceptRefinementRound.State.KEEP_CURRENT);
        JsonNode decision = mapper.readTree(round.getDecisionJson());
        assertThat(decision.path("selectedProposals")).isEmpty();
        assertThat(decision.path("declinedProposalKeys")).hasSize(3);
        assertThat(decision.path("keepCurrent").asBoolean()).isTrue();

        ConceptRefinementRound another = awaiting(proposals(
            proposal("price", "10,000원", "12,500원", "MARKET", "E-1")));
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(another));
        var anotherSet = contract.proposalSet(another);
        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "keep-2", 1,
            anotherSet.hash(), List.of(key(anotherSet, "price")), true))
            .isInstanceOf(BusinessException.class);
        assertThat(another.getDecisionJson()).isNull();
    }

    @Test
    void sameIdempotencyDecisionReplaysAndChangedSelectionConflicts() {
        var set = contract.proposalSet(round);
        String price = key(set, "price");
        service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1, set.hash(), List.of(price), false);
        String stored = round.getDecisionJson();

        service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1, set.hash(), List.of(price), false);
        assertThat(round.getDecisionJson()).isEqualTo(stored);

        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1,
            set.hash(), List.of(key(set, "targetUsers")), false))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void onlyAwaitingDecisionCanAcceptANewCommand() {
        ConceptRefinementRound proposing = ConceptRefinementRound.start(PROJECT_ID, source,
            "task-2", "start-2", HASH);
        when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT_ID))
            .thenReturn(Optional.of(proposing));
        var empty = contract.proposalSet(proposing);

        assertThatThrownBy(() -> service.decide(OWNER_ID, PROJECT_ID, "decision-1", 1,
            empty.hash(), List.of(), true))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private ConceptRefinementRound awaiting(ArrayNode proposals) {
        ConceptRefinementRound value = ConceptRefinementRound.start(PROJECT_ID, source,
            "task-1", "start-1", HASH);
        value.materialize(proposals.toString(), "[]", true);
        return value;
    }

    private ArrayNode proposals(ObjectNode... values) {
        ArrayNode result = mapper.createArrayNode();
        for (ObjectNode value : values) result.add(value);
        return result;
    }

    private ObjectNode proposal(String field, Object current, Object proposed,
            String sourceType, String evidence) {
        ObjectNode value = mapper.createObjectNode();
        value.put("fieldKey", field);
        value.set("currentValue", mapper.valueToTree(current));
        value.set("proposedValue", mapper.valueToTree(proposed));
        value.put("title", field + " 변경");
        value.put("rationale", "검증 근거 반영");
        value.put("source", sourceType);
        value.putArray("evidenceIds").add(evidence);
        if ("LEGAL".equals(sourceType)) value.put("legalRef", "법률 제1조");
        return value;
    }

    private String key(ConceptRefinementDecisionContract.ProposalSet set, String field) {
        return set.projected().valueStream()
            .filter(value -> field.equals(value.path("fieldKey").asText()))
            .findFirst().orElseThrow().path("proposalKey").asText();
    }

    private JsonNode find(JsonNode proposals, String field) {
        return proposals.valueStream()
            .filter(value -> field.equals(value.path("fieldKey").asText()))
            .findFirst().orElseThrow();
    }
}
