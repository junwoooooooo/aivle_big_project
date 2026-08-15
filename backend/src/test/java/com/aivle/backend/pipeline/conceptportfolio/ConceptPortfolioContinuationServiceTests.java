package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.*;
import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.service.*;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioContinuationServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConceptPortfolioRunRepository runs = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
    private final ConceptPortfolioContinuationRepository continuations = mock(ConceptPortfolioContinuationRepository.class);
    private final ConceptInputRequestRepository inputs = mock(ConceptInputRequestRepository.class);
    private final ConceptInputResponseRepository responses = mock(ConceptInputResponseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
    private final Project project = mock(Project.class);
    private final User user = mock(User.class);
    private final TaskRun task = mock(TaskRun.class);
    private ConceptPortfolioContinuationService service;
    private ConceptPortfolioContinuation continuation;
    private ConceptInputRequest request;

    @BeforeEach
    void setUp() {
        reset(runs, concepts, continuations, inputs, responses, users, taskRuns, hasher,
            events, run, project, user, task);
        when(run.getId()).thenReturn("run"); when(run.getProject()).thenReturn(project);
        when(run.isCurrent()).thenReturn(true); when(run.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.NEEDS_INPUT);
        when(project.getId()).thenReturn(42L);
        when(runs.findOwned(7L, 42L, "run")).thenReturn(Optional.of(run));
        when(runs.findLocked("run")).thenReturn(Optional.of(run));
        continuation = ConceptPortfolioContinuation.create(run, "1.0", "sha256:" + "a".repeat(64),
            "{\"contextVersion\":\"1.0\"}");
        request = ConceptInputRequest.open(run, continuation, "candidate", "lineage", "CANDIDATE",
            "질문", "이유", "답변", "요약", "[\"판매 주체\"]", "[\"sellerRole\"]",
            "{\"candidateId\":\"candidate\"}", "sha256:" + "b".repeat(64));
        when(inputs.findLocked(request.getId())).thenReturn(Optional.of(request));
        when(continuations.findByRunIdAndDeletedAtIsNull("run")).thenReturn(Optional.of(continuation));
        when(users.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(task.getId()).thenReturn("continuation-task");
        when(taskRuns.create(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyInt())).thenReturn(task);
        when(hasher.hash(eq(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn("sha256:" + "c".repeat(64));
        when(responses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ConceptPortfolioContinuationService(runs, concepts, continuations, inputs,
            responses, users, taskRuns, hasher, new ConceptPortfolioJsonHasher(mapper), events,
            new EffectiveAffectedFieldResolver(mapper), mapper,
            Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesResponseAnswersRequestAndCreatesNewTaskForSamePortfolioSubject() {
        var result = service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"sellerRole\":\"직접 판매\"}"), "idem", null));

        assertThat(result.inputRequestStatus()).isEqualTo("ANSWERED");
        assertThat(request.getAnsweredAt()).isEqualTo(LocalDateTime.parse("2026-08-10T00:00:00"));
        assertThat(request.getContinuationTaskRunId()).isEqualTo("continuation-task");
        verify(responses).save(any(ConceptInputResponse.class));
        ArgumentCaptor<String> inputJson = ArgumentCaptor.forClass(String.class);
        verify(taskRuns).create(eq(7L), eq(42L), eq(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE),
            eq("CONCEPT_PORTFOLIO_RUN"), eq("run"), inputJson.capture(), anyString(), eq("idem"),
            anyString(), eq(2));
        assertThat(mapper.readTree(inputJson.getValue()).path("confirmedFacts").path("sellerRole").asText())
            .isEqualTo("직접 판매");
        assertThat(inputJson.getValue()).contains("continuationContext", "continuationArtifact");
        verify(run).attachContinuationTask("continuation-task");
    }

    @Test
    void enforcesCandidateScopeFactTypesStateAndIdempotency() {
        assertCode(() -> service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"paymentFlow\":\"현금\"}"), "bad", null)),
            ErrorCode.ANALYSIS_INPUT_INVALID);
        assertCode(() -> service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"providerRole\":\"직접\"}"), "bad2", null)),
            ErrorCode.ANALYSIS_INPUT_INVALID);

        ConceptInputRequest global = ConceptInputRequest.open(run, null, null, null, "GLOBAL",
            "전역", null, null, "요약", "[]", "[]", null, "sha256:" + "d".repeat(64));
        when(inputs.findLocked(global.getId())).thenReturn(Optional.of(global));
        assertCode(() -> service.submit(7L, 42L, "run", global.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"sellerRole\":\"직접\"}"), "global", null)),
            ErrorCode.ANALYSIS_INPUT_INVALID);

        ConceptInputResponse existing = ConceptInputResponse.create(request, user,
            "{\"confirmedFacts\":{\"sellerRole\":\"직접 판매\"},\"note\":null}", "replay");
        when(responses.findByInputRequestIdAndIdempotencyKeyAndDeletedAtIsNull(request.getId(), "replay"))
            .thenReturn(Optional.of(existing));
        assertThat(service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"sellerRole\":\"직접 판매\"}"), "replay", null))
            .inputResponseId()).isEqualTo(existing.getId());
        assertCode(() -> service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"sellerRole\":\"중개 판매\"}"), "replay", null)),
            ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void requiresEveryEffectiveAffectedFieldInOneSubmission() {
        ConceptInputRequest multiple = ConceptInputRequest.open(run, continuation,
            "candidate-2", "lineage-2", "CANDIDATE", "개인정보와 결제 방식", "이유", "답변", "요약",
            "[]", "[\"personalDataUsage\",\"paymentFlow\"]",
            "{\"candidateId\":\"candidate-2\"}", "sha256:" + "f".repeat(64));
        when(inputs.findLocked(multiple.getId())).thenReturn(Optional.of(multiple));

        assertCode(() -> service.submit(7L, 42L, "run", multiple.getId(),
            new SubmitInputResponseRequest(
                mapper.readTree("{\"personalDataUsage\":[\"이름\"]}"), "partial", null)),
            ErrorCode.ANALYSIS_INPUT_INVALID);
    }

    @Test
    void retriesTechnicalFailureWithStoredResponseWithoutCreatingAnotherResponse() {
        request.answer("failed-task", LocalDateTime.parse("2026-08-10T00:00:00"));
        assertCode(() -> service.submit(7L, 42L, "run", request.getId(),
            new SubmitInputResponseRequest(mapper.readTree("{\"sellerRole\":\"직접 판매\"}"),
                "second-answer", null)), ErrorCode.ANALYSIS_INPUT_INVALID);
        ConceptInputResponse stored = ConceptInputResponse.create(request, user,
            "{\"confirmedFacts\":{\"sellerRole\":\"직접 판매\"},\"note\":null}", "original");
        when(responses.findFirstByInputRequestIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(request.getId()))
            .thenReturn(Optional.of(stored));
        TaskRun failed = mock(TaskRun.class);
        when(failed.getState()).thenReturn(TaskRunState.FAILED);
        when(taskRuns.getOwned(7L, 42L, "failed-task")).thenReturn(failed);

        var result = service.retry(7L, 42L, "run", request.getId(),
            new RetryContinuationRequest("retry-key"));

        assertThat(result.inputResponseId()).isEqualTo(stored.getId());
        assertThat(request.getStatus()).isEqualTo(ConceptInputRequestStatus.ANSWERED);
        assertThat(request.getContinuationTaskRunId()).isEqualTo("continuation-task");
        verify(responses, never()).save(any());
        verify(taskRuns).create(eq(7L), eq(42L), eq(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE),
            eq("CONCEPT_PORTFOLIO_RUN"), eq("run"), anyString(), anyString(), eq("retry-key"),
            anyString(), eq(2));
    }

    @Test
    void listRequiresOwnedRunAndUsesSafeQuestionFallback() {
        when(inputs.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc("run", 42L))
            .thenReturn(List.of(request));
        assertThat(service.list(7L, 42L, "run")).singleElement().satisfies(value -> {
            assertThat(value.question()).isEqualTo("질문");
            assertThat(value.nextAction()).isEqualTo("PROVIDE_REQUIRED_INPUT");
        });
        when(runs.findOwned(8L, 42L, "run")).thenReturn(Optional.empty());
        assertCode(() -> service.list(8L, 42L, "run"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void listRecoversExistingUnresolvedRequestWithoutMutatingStoredFields() {
        ConceptInputRequest unresolved = ConceptInputRequest.open(run, continuation,
            "candidate-2", "lineage-2", "CANDIDATE",
            "What specific personal data will be collected and how will it be used? "
                + "What payment methods will be accepted and how will they be processed?",
            "Legal review requires actual operating facts", "답변", "요약", "[]", "[]",
            "{\"candidateId\":\"candidate-2\"}", "sha256:" + "e".repeat(64));
        when(inputs.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc("run", 42L))
            .thenReturn(List.of(unresolved));

        var value = service.list(7L, 42L, "run").get(0);

        assertThat(value.affectedFields()).extracting(JsonNode::asText)
            .containsExactly("personalDataUsage", "paymentFlow");
        assertThat(value.nextAction()).isEqualTo("PROVIDE_REQUIRED_INPUT");
        assertThat(unresolved.getAffectedFieldsJson()).isEqualTo("[]");
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo(expected));
    }
}
