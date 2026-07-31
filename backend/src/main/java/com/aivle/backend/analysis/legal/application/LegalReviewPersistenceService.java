package com.aivle.backend.analysis.legal.application;

import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.analysis.legal.feedback.ReviewCycle;
import com.aivle.backend.analysis.legal.feedback.ReviewCycleRepository;
import com.aivle.backend.analysis.legal.feedback.ReviewCycleService;
import com.aivle.backend.analysis.legal.feedback.ReviewDiffService;
import com.aivle.backend.analysis.legal.repository.*;
import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.integration.ai.legal.LegalReviewAiResponse;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.audit.AuditEventType;

@Service
@RequiredArgsConstructor
public class LegalReviewPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(LegalReviewPersistenceService.class);

    private final AnalysisJobRepository jobs;
    private final LegalReviewRepository reviews;
    private final LegalFindingRepository findings;
    private final LegalReviewQuestionRepository questions;
    private final ReviewCycleRepository cycles;
    private final ReviewCycleService cycleService;
    private final ReviewDiffService diffService;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;
    private final DomainAuditService audit;

    @Transactional
    public Long complete(JobClaim claim, LegalReviewJobContext context, LegalReviewAiResponse result) {
        validate(result);
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        var plan = job.getSourceStructuredPlan();
        var existing = reviews.findByStructuredPlanIdAndPromptVersionAndDeletedAtIsNull(
            plan.getId(), LegalReviewPolicy.PROMPT_VERSION);
        LegalReview review = existing.orElseGet(() -> persistNewReview(job, plan, context, result));
        job.complete(claim.claimToken(), claim.attempt(), JobStatus.SUCCEEDED,
            "LEGAL_REVIEW", review.getId(), LocalDateTime.now(jobClock));
        audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
            AuditEventType.LEGAL_REVIEW_COMPLETED, "LegalReview", review.getId(), null,
            Map.of("legalReviewId", review.getId().toString(),
                "resultStatus", review.getStatus().name(),
                "overallRiskLevel", review.getRiskLevel().name()));
        return review.getId();
    }

    private LegalReview persistNewReview(
        AnalysisJob job, StructuredPlan plan,
        LegalReviewJobContext context, LegalReviewAiResponse result
    ) {
        LocalDateTime now = LocalDateTime.now(jobClock);
        ReviewMode mode = context.mode() == null ? ReviewMode.FULL : context.mode();
        LegalReview parentReview = context.parentReviewId() == null ? null
            : reviews.findById(context.parentReviewId()).orElse(null);
        boolean incremental = mode == ReviewMode.INCREMENTAL && parentReview != null;
        Set<LegalCategory> rerunCategories = incremental
            ? context.rerunCategories() : EnumSet.allOf(LegalCategory.class);

        List<QuestionDraft> questionDrafts =
            mergeQuestions(incremental, parentReview, rerunCategories, result, plan);

        boolean hasOpenQuestion = questionDrafts.stream()
            .anyMatch(draft -> draft.status() == LegalQuestionStatus.OPEN);
        String canonical = json(result);
        LegalReview review = reviews.save(LegalReview.completed(
            job.getProject(), job, plan,
            hasOpenQuestion ? LegalReviewStatus.NEEDS_REVIEW : LegalReviewStatus.COMPLETED,
            result.overallRiskLevel(), result.summary(), LegalReviewPolicy.DISCLAIMER,
            result.provider(), result.model(), LegalReviewPolicy.PROMPT_VERSION,
            sha256(LegalReviewPolicy.PROMPT), sha256(canonical), context.snapshotJson(), now));
        review.attachRunMetadata(
            context.cycleId(), parentReview, mode,
            json(context.changedSections()),
            json(rerunCategories.stream().map(Enum::name).sorted().toList()),
            json(incremental
                ? context.carriedCategories().stream().map(Enum::name).sorted().toList()
                : List.of()));

        persistFindings(review, parentReview, incremental, context, result);
        int questionOrder = 1;
        for (QuestionDraft draft : questionDrafts) {
            if (draft.carriedFrom() != null) {
                questions.save(LegalReviewQuestion.carriedFrom(
                    draft.carriedFrom(), review, questionOrder++));
            } else {
                questions.save(LegalReviewQuestion.open(
                    review, questionOrder++, draft.question(), draft.reason(), draft.categoriesJson()));
            }
        }

        if (context.cycleId() != null) {
            ReviewCycle cycle = cycles.findById(context.cycleId()).orElse(null);
            if (cycle != null) {
                diffService.applyRevisionRequests(
                    cycle, review, result.revisionRequests(), rerunCategories,
                    plan.getVersionNumber());
                boolean converged = cycleService.recomputeState(cycle, review);
                if (converged) {
                    audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
                        AuditEventType.REVIEW_CYCLE_CONVERGED, "ReviewCycle", cycle.getId(), null,
                        Map.of("reviewCycleId", cycle.getId().toString(),
                            "planVersion", plan.getVersionNumber().toString()));
                }
            }
        }
        return review;
    }

    /** 증분이면 승계 범주 finding을 직전 리뷰에서 복사하고 재실행 범주만 새 결과를 쓴다. */
    private void persistFindings(
        LegalReview review, LegalReview parentReview, boolean incremental,
        LegalReviewJobContext context, LegalReviewAiResponse result
    ) {
        Map<LegalCategory, LegalReviewAiResponse.Finding> resultByCategory =
            new EnumMap<>(LegalCategory.class);
        for (var item : result.findings()) {
            resultByCategory.put(item.category(), item);
        }
        Map<LegalCategory, LegalFinding> parentByCategory = new EnumMap<>(LegalCategory.class);
        if (incremental) {
            for (var parentFinding : findings
                .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(parentReview.getId())) {
                parentByCategory.put(parentFinding.getCategory(), parentFinding);
            }
        }
        List<LegalCategory> carriedSaved = new ArrayList<>();
        int order = 1;
        for (LegalCategory category : LegalCategory.values()) {
            if (incremental && context.carriedCategories().contains(category)
                && parentByCategory.containsKey(category)) {
                findings.save(LegalFinding.carriedFrom(
                    parentByCategory.get(category), review, order++));
                carriedSaved.add(category);
            } else {
                var item = resultByCategory.get(category);
                findings.save(LegalFinding.create(
                    review, category, order++, item.applicability(), item.riskLevel(),
                    item.title(), item.finding(), item.rationale(), item.recommendedAction(),
                    json(item.evidence()),
                    item.reasoning() == null ? null : json(item.reasoning()),
                    json(item.sourceSectionCodes()),
                    item.requiresProfessionalReview(), item.confidence()));
            }
        }
        if (!carriedSaved.isEmpty()) {
            log.info("승계 finding 복사 완료: categories={} (파이프라인 결과 대신 직전 리뷰 결과 사용)",
                carriedSaved);
        }
    }

    /**
     * 질문 병합 규칙 (§4-4):
     * 승계 범주의 OPEN 질문은 복사, 재실행 범주는 정규화 텍스트로 매칭(미매칭 → resolved 기록),
     * ANSWERED와 동일한 신규 질문은 억제(재질문 방지).
     */
    private List<QuestionDraft> mergeQuestions(
        boolean incremental, LegalReview parentReview, Set<LegalCategory> rerunCategories,
        LegalReviewAiResponse result, StructuredPlan plan
    ) {
        List<QuestionDraft> drafts = new ArrayList<>();
        List<LegalReviewAiResponse.Question> pipelineQuestions =
            new ArrayList<>(result.questions());
        Set<String> answeredTexts = new HashSet<>();
        if (incremental) {
            var parentQuestions = questions
                .findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(parentReview.getId());
            for (var parentQuestion : parentQuestions) {
                if (parentQuestion.getStatus() == LegalQuestionStatus.ANSWERED) {
                    answeredTexts.add(normalize(parentQuestion.getQuestion()));
                }
            }
            for (var parentQuestion : parentQuestions) {
                if (parentQuestion.getStatus() != LegalQuestionStatus.OPEN
                    || parentQuestion.getResolvedInVersion() != null) {
                    continue;
                }
                List<LegalCategory> categories = parseCategories(parentQuestion.getCategoriesJson());
                boolean inRerun = categories.stream().anyMatch(rerunCategories::contains);
                if (!inRerun) {
                    drafts.add(QuestionDraft.carried(parentQuestion));
                    continue;
                }
                var matched = pipelineQuestions.stream()
                    .filter(q -> normalize(q.question()).equals(normalize(parentQuestion.getQuestion())))
                    .findFirst();
                if (matched.isPresent()) {
                    pipelineQuestions.remove(matched.get());
                    drafts.add(QuestionDraft.carried(parentQuestion));
                } else {
                    parentQuestion.markResolved(plan.getVersionNumber());
                    log.info("질문 '{}' → v{}에서 해소 (재실행 범주에서 재방출되지 않음)",
                        parentQuestion.getQuestion(), plan.getVersionNumber());
                }
            }
        }
        for (var question : pipelineQuestions) {
            if (answeredTexts.contains(normalize(question.question()))) {
                log.info("이미 답변된 질문과 동일하여 억제: {}", question.question());
                continue;
            }
            String categoriesJson = question.categories().isEmpty() ? null
                : json(question.categories().stream().map(Enum::name).toList());
            drafts.add(QuestionDraft.fresh(question.question(), question.reason(), categoriesJson));
        }
        return drafts;
    }

    private record QuestionDraft(
        String question, String reason, String categoriesJson,
        LegalReviewQuestion carriedFrom, LegalQuestionStatus status
    ) {
        static QuestionDraft carried(LegalReviewQuestion parent) {
            return new QuestionDraft(parent.getQuestion(), parent.getReason(),
                parent.getCategoriesJson(), parent, parent.getStatus());
        }

        static QuestionDraft fresh(String question, String reason, String categoriesJson) {
            return new QuestionDraft(question, reason, categoriesJson, null, LegalQuestionStatus.OPEN);
        }
    }

    private List<LegalCategory> parseCategories(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> names = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            List<LegalCategory> categories = new ArrayList<>();
            for (String name : names) {
                try {
                    categories.add(LegalCategory.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                    // 알 수 없는 범주명은 무시
                }
            }
            return categories;
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private void validate(LegalReviewAiResponse result) {
        if (result == null || result.findings() == null
            || result.findings().size() != LegalCategory.values().length) {
            throw new IllegalArgumentException("legal AI result must contain exactly ten categories");
        }
        EnumSet<LegalCategory> seen = EnumSet.noneOf(LegalCategory.class);
        for (var item : result.findings()) {
            if (item == null || item.category() == null || item.applicability() == null
                || item.riskLevel() == null || !seen.add(item.category())) {
                throw new IllegalArgumentException("legal AI result contains invalid categories");
            }
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("legal result serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
