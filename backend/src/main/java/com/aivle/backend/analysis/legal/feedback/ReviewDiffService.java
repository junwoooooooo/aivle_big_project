package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.LegalFinding;
import com.aivle.backend.analysis.legal.entity.LegalReview;
import com.aivle.backend.analysis.legal.repository.LegalFindingRepository;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.integration.ai.legal.LegalReviewAiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 결정론적 diff 계약.
 * 수정 요청의 정체성 키 = (category, anchorSectionCode) — 사이클 내에서 유지된다.
 * RESOLVED는 삭제가 아니라 resolvedInVersion 기록이다.
 */
@Service
@RequiredArgsConstructor
public class ReviewDiffService {
    private static final Logger log = LoggerFactory.getLogger(ReviewDiffService.class);
    private static final Pattern ACTION_PATTERN = Pattern.compile("^(.+?)\\s*\\(([^()]+)\\)$");
    private static final String CONDITIONAL_TIMING = "계획 실행 시";

    private final RevisionRequestRepository revisionRequests;
    private final RevisionSuggestionRepository suggestions;
    private final LegalFindingRepository findings;

    public record DiffSummary(int resolved, int added, int maintained) {}

    /**
     * 재검토 결과의 수정 요청을 기존 요청과 대조해 반영한다.
     * <ul>
     *   <li>승계 범주의 기존 요청: 건드리지 않음 (CARRIED)</li>
     *   <li>재실행 범주 + 같은 키 재방출: OPEN이면 내용 갱신, ACCEPTED였다면 새 행(수정이 안 먹은 것)</li>
     *   <li>재실행 범주 + 미방출: resolvedInVersion 기록 (행 존속)</li>
     *   <li>새 키: OPEN 행 신설</li>
     * </ul>
     */
    public void applyRevisionRequests(
        ReviewCycle cycle,
        LegalReview review,
        List<LegalReviewAiResponse.RevisionRequestPayload> payloads,
        Set<LegalCategory> rerunCategories,
        int planVersionNumber
    ) {
        Map<String, LegalReviewAiResponse.RevisionRequestPayload> payloadByKey = new LinkedHashMap<>();
        for (var payload : payloads) {
            BusinessPlanSectionCode sectionCode = parseSectionCode(payload.anchorSectionCode());
            if (payload.category() == null || sectionCode == null
                || payload.anchorQuote() == null || payload.anchorQuote().isBlank()) {
                log.warn("무효한 수정 요청 payload를 건너뜁니다: {}", payload);
                continue;
            }
            payloadByKey.put(key(payload.category(), sectionCode), payload);
        }

        List<RevisionRequest> existing =
            revisionRequests.findByReviewCycleIdAndDeletedAtIsNullOrderById(cycle.getId());
        for (RevisionRequest request : existing) {
            if (request.getResolvedInVersion() != null
                || request.getStatus() == RevisionRequestStatus.DISMISSED) {
                continue;
            }
            if (!rerunCategories.contains(request.getCategory())) {
                continue; // 승계 범주 — 재실행하지 않았으므로 판정 불가, 그대로 유지
            }
            var matched = payloadByKey.remove(key(request.getCategory(), request.getAnchorSectionCode()));
            if (matched == null) {
                request.markResolved(planVersionNumber);
                log.info("수정 요청 {} → v{}에서 해결 (행 보존)", request.getId(), planVersionNumber);
                continue;
            }
            if (request.getStatus() == RevisionRequestStatus.OPEN) {
                request.refresh(matched.anchorQuote(), matched.rationale());
                replaceSuggestions(request, matched);
            } else {
                // ACCEPTED였는데 재방출 — 반영한 수정으로 문제가 해소되지 않았다는 뜻이므로 새 요청으로 기록
                createRequest(cycle, review, matched);
            }
        }
        for (var payload : payloadByKey.values()) {
            createRequest(cycle, review, payload);
        }
    }

    /** diff 배너: 수정 요청 ∪ 할 일(액션 문자열 키). 질문은 별도 카운트 (설계 D5). */
    public DiffSummary summarize(LegalReview review) {
        LegalReview parent = review.getParentReview();
        if (parent == null) {
            return null;
        }
        int requestResolved = 0;
        int requestAdded = 0;
        int requestMaintained = 0;
        for (RevisionRequest request :
            revisionRequests.findByReviewCycleIdAndDeletedAtIsNullOrderById(review.getReviewCycleId())) {
            if (request.getStatus() == RevisionRequestStatus.DISMISSED) {
                continue;
            }
            boolean raisedNow = request.getRaisedInReview().getId().equals(review.getId());
            if (Objects.equals(request.getResolvedInVersion(), review.getVersionNumber())) {
                requestResolved++;
            } else if (request.getResolvedInVersion() == null && raisedNow) {
                requestAdded++;
            } else if (request.getResolvedInVersion() == null) {
                requestMaintained++;
            }
        }

        Set<String> parentTodos = nowActions(
            findings.findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(parent.getId()));
        Set<String> currentTodos = nowActions(
            findings.findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(review.getId()));
        int todoResolved = (int) parentTodos.stream().filter(t -> !currentTodos.contains(t)).count();
        int todoAdded = (int) currentTodos.stream().filter(t -> !parentTodos.contains(t)).count();
        int todoMaintained = (int) currentTodos.stream().filter(parentTodos::contains).count();

        return new DiffSummary(
            requestResolved + todoResolved,
            requestAdded + todoAdded,
            requestMaintained + todoMaintained);
    }

    /** "액션 (시점) / ..." 문자열에서 즉시 할 일만 추출 — 프론트 collectActions와 동일 규칙. */
    public static Set<String> nowActions(List<LegalFinding> findingList) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (LegalFinding finding : findingList) {
            String recommendation = finding.getRecommendation();
            if (recommendation == null) {
                continue;
            }
            for (String part : recommendation.split(" / ")) {
                Matcher matcher = ACTION_PATTERN.matcher(part.strip());
                if (!matcher.matches()) {
                    continue;
                }
                String action = matcher.group(1).strip();
                String timing = matcher.group(2).strip();
                if (!action.isEmpty() && !CONDITIONAL_TIMING.equals(timing)) {
                    actions.add(action);
                }
            }
        }
        return actions;
    }

    private void createRequest(
        ReviewCycle cycle, LegalReview review, LegalReviewAiResponse.RevisionRequestPayload payload
    ) {
        RevisionRequest request = revisionRequests.save(RevisionRequest.open(
            cycle, review, payload.category(),
            parseSectionCode(payload.anchorSectionCode()),
            payload.anchorQuote(), payload.rationale()));
        saveSuggestions(request, payload);
    }

    private void replaceSuggestions(
        RevisionRequest request, LegalReviewAiResponse.RevisionRequestPayload payload
    ) {
        suggestions.findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(request.getId())
            .forEach(RevisionSuggestion::softDelete);
        saveSuggestions(request, payload);
    }

    private void saveSuggestions(
        RevisionRequest request, LegalReviewAiResponse.RevisionRequestPayload payload
    ) {
        int order = 1;
        for (var suggestion : payload.suggestions()) {
            suggestions.save(RevisionSuggestion.create(
                request, suggestion.label(), suggestion.newText(), order++));
        }
    }

    private BusinessPlanSectionCode parseSectionCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            return BusinessPlanSectionCode.valueOf(code);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String key(LegalCategory category, BusinessPlanSectionCode sectionCode) {
        return category.name() + ":" + sectionCode.name();
    }
}
