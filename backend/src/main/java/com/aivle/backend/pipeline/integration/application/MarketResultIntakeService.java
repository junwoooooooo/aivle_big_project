package com.aivle.backend.pipeline.integration.application;

import static com.aivle.backend.pipeline.integration.api.MarketResultApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.integration.domain.MarketAnalysisResult;
import com.aivle.backend.pipeline.integration.domain.ModuleRunStatus;
import com.aivle.backend.pipeline.integration.repository.MarketAnalysisResultRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class MarketResultIntakeService {
    public static final String CONTRACT = "market-analysis-result-v1";
    private final ProjectRepository projects;
    private final ModuleRunRepository runs;
    private final MarketAnalysisResultRepository results;
    private final ConceptSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository snapshots;
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;
    private final SnapshotStaleness snapshotStaleness;

    @Transactional
    public MarketResultView intake(MarketResultIntakeRequest request) {
        validate(request);
        var run = runs.findById(request.moduleRunId()).filter(value -> value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module Run을 찾을 수 없습니다."));
        if (!run.getInputSnapshotId().equals(request.inputSnapshotId()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        var existing = results.findById(request.moduleRunId());
        if (existing.isPresent()) {
            if (!existing.get().getResultHash().equals(request.resultHash()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 다른 결과를 수신한 Module Run입니다.");
            return view(existing.get());
        }
        MarketAnalysisResult saved = results.save(MarketAnalysisResult.received(request.moduleRunId(), run.getProjectId(),
            request.inputSnapshotId(), request.status(), request.resultReference(), mapper.writeValueAsString(request.summary()),
            mapper.writeValueAsString(request.competitors()), request.completedAt(), request.resultHash()));
        run.receiveResult(ModuleRunStatus.valueOf(request.status()), request.resultReference(), request.resultHash(), request.completedAt());
        return view(saved);
    }

    @Transactional
    public MarketResultView importFixture(Long ownerId, Long projectId, MarketResultIntakeRequest request) {
        requireOwned(ownerId, projectId);
        var run = runs.findByIdAndProjectIdAndDeletedAtIsNull(request.moduleRunId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!run.getInputSnapshotId().equals(request.inputSnapshotId()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        return intake(request);
    }

    @Transactional
    public MarketResultView createLocalStub(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        var run = runs.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "시장분석 Handoff를 먼저 준비해 주세요."));
        ObjectNode summary = mapper.createObjectNode();
        summary.put("marketSummary", "초기에는 지역과 계약 고객을 좁혀 공식 가격과 운영 범위를 반복 검증해야 합니다.");
        summary.putArray("targetCustomerImplications").add("계약과 운영 책임이 명확한 고객군을 우선 검증합니다.");
        summary.putArray("pricingAndChannelImplications").add("직접 영업과 제휴 채널의 전환율을 비교합니다.");
        Instant now = Instant.now();
        var sources = List.of(new SourceReference("개발용 공식 근거 예시", "https://example.com/official-evidence", now));
        ObjectNode price = mapper.createObjectNode();
        price.put("display", "공식 문의 기반 견적");
        price.put("sourceUrl", "https://example.com/pricing");
        var competitors = List.of(new Competitor("개발용 경쟁 제품", "Example Operations",
            "https://example.com/product", "로컬 검증용 fixture입니다.", price, List.of("계약 관리"),
            "초기 계약 고객", now, sources, "VERIFIED"));
        var unsigned = new MarketResultIntakeRequest(CONTRACT, run.getId(), run.getInputSnapshotId(), "COMPLETED",
            "local-stub://" + run.getId(), summary, competitors, now, "sha256:pending");
        var signed = new MarketResultIntakeRequest(unsigned.contract(), unsigned.moduleRunId(), unsigned.inputSnapshotId(),
            unsigned.status(), unsigned.resultReference(), unsigned.summary(), unsigned.competitors(),
            unsigned.completedAt(), expectedHash(unsigned));
        return intake(signed);
    }

    @Transactional(readOnly = true)
    public MarketResultView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return results.findFirstByProjectIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId)
            .map(this::view).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "시장분석 결과가 아직 없습니다."));
    }

    public String expectedHash(MarketResultIntakeRequest request) {
        ObjectNode body = (ObjectNode) mapper.valueToTree(request);
        body.remove("resultHash");
        return hasher.hash(body);
    }

    private void validate(MarketResultIntakeRequest request) {
        if (!CONTRACT.equals(request.contract()) || !("COMPLETED".equals(request.status())
                || "FAILED".equals(request.status()) || "NEEDS_INPUT".equals(request.status())))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 market-analysis-result-v1 상태입니다.");
        if ("COMPLETED".equals(request.status()) && request.competitors().isEmpty())
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "완료 결과에는 검증된 경쟁제품이 필요합니다.");
        if (!request.resultHash().matches("^sha256:[0-9a-f]{64}$") || !request.resultHash().equals(expectedHash(request)))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "resultHash가 결과 본문과 일치하지 않습니다.");
        for (var competitor : request.competitors()) {
            if (!competitor.officialUrl().startsWith("https://") || competitor.sourceReferences().isEmpty()
                || "UNVERIFIED".equalsIgnoreCase(competitor.verificationStatus()))
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "경쟁제품에는 검증 상태와 실제 Source Reference가 필요합니다.");
        }
    }

    private MarketResultView view(MarketAnalysisResult result) {
        String currentSnapshot = currentSnapshotId(result.getProjectId());
        boolean stale = snapshotStaleness.isStale(result.getInputSnapshotId(), currentSnapshot);
        List<Competitor> competitors = mapper.readValue(result.getCompetitorsJson(), new TypeReference<List<Competitor>>() {});
        return new MarketResultView(CONTRACT, result.getModuleRunId(), result.getInputSnapshotId(),
            stale ? "STALE" : result.getStatus(), stale, result.getResultReference(), mapper.readTree(result.getSummaryJson()),
            competitors, result.getCompletedAt(), result.getResultHash());
    }

    private String currentSnapshotId(Long projectId) {
        return selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .flatMap(selection -> snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId))
            .map(value -> value.getId()).orElse(null);
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
