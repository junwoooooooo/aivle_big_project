package com.aivle.backend.pipeline.integration.application;

import static com.aivle.backend.pipeline.integration.api.MarketResultApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.integration.domain.MarketAnalysisResult;
import com.aivle.backend.pipeline.integration.domain.PlanningChangeProposal;
import com.aivle.backend.pipeline.integration.domain.ProposalDecisionStatus;
import com.aivle.backend.pipeline.integration.domain.ModuleRunStatus;
import com.aivle.backend.pipeline.integration.repository.MarketAnalysisResultRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.integration.repository.PlanningChangeProposalRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class MarketResultIntakeService {
    public static final String CONTRACT = "market-analysis-result-v1";
    private final ProjectRepository projects;
    private final ModuleRunRepository runs;
    private final MarketAnalysisResultRepository results;
    private final PlanningChangeProposalRepository proposals;
    private final ConceptSelectionRepository selections;
    private final SelectedConceptSnapshotRepository snapshots;
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;
    private final SnapshotStaleness snapshotStaleness;

    @Transactional
    public MarketResultView intake(MarketResultIntakeRequest request) {
        validate(request);
        var run = runs.findById(request.moduleRunId())
            .filter(value -> value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Module Run을 찾을 수 없습니다."));
        if (!run.getInputSnapshotId().equals(request.inputSnapshotId()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        var existing = results.findById(request.moduleRunId());
        if (existing.isPresent()) {
            if (!existing.get().getResultHash().equals(request.resultHash()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "이미 다른 결과가 수신된 Module Run입니다.");
            return view(existing.get());
        }
        MarketAnalysisResult saved = results.save(MarketAnalysisResult.received(request.moduleRunId(), run.getProjectId(),
            request.inputSnapshotId(), request.status(), request.resultReference(), mapper.writeValueAsString(request.summary()),
            mapper.writeValueAsString(request.competitors()), request.completedAt(), request.resultHash()));
        for (var proposal : request.planningChangeProposals()) {
            proposals.save(PlanningChangeProposal.pending(proposal.proposalId(), run.getId(), run.getProjectId(),
                proposal.meaningfulTitle().strip(), mapper.writeValueAsString(proposal.affectedFields()),
                mapper.writeValueAsString(proposal.before()), mapper.writeValueAsString(proposal.after()), proposal.reason().strip(),
                mapper.writeValueAsString(proposal.evidenceReferences()), mapper.writeValueAsString(proposal.impactAreas())));
        }
        run.receiveResult(ModuleRunStatus.valueOf(request.status()), request.resultReference(), request.resultHash(), request.completedAt());
        return view(saved);
    }

    @Transactional
    public MarketResultView importFixture(Long ownerId, Long projectId, MarketResultIntakeRequest request) {
        requireOwned(ownerId, projectId);
        var run = runs.findByIdAndProjectIdAndDeletedAtIsNull(request.moduleRunId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!run.getInputSnapshotId().equals(request.inputSnapshotId())) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        return intake(request);
    }

    @Transactional
    public MarketResultView createLocalStub(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        var run = runs.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "먼저 시장분석 Handoff를 준비해 주세요."));
        ObjectNode summary = mapper.createObjectNode();
        summary.put("marketSummary", "초기에는 지역과 계약 고객을 좁혀 검증하고, 공식 가격과 운영 범위를 반복 확인해야 합니다.");
        summary.putArray("targetCustomerImplications")
            .add("관리사무소와 운영 계약을 맺을 수 있는 공동주택 단지를 우선 고객으로 검증합니다.")
            .add("입주민 개인보다 계약과 운영 책임이 명확한 담당자를 초기 구매자로 봅니다.");
        summary.putArray("pricingAndChannelImplications")
            .add("단지 규모별 월 구독료와 건별 운영비를 분리해 제시합니다.")
            .add("직접 운영보다 허가된 지역 파트너 채널을 우선 검토합니다.");
        Instant now = Instant.now();
        var sources = List.of(new SourceReference("개발용 공식 근거 예시", "https://example.com/official-evidence", now));
        ObjectNode price = mapper.createObjectNode();
        price.put("display", "공식 문의 기반 견적"); price.put("sourceUrl", "https://example.com/pricing");
        var competitors = List.of(new Competitor("지역 생활관리 서비스", "Example Operations",
            "https://example.com/product", "계약 단지에 생활관리 운영을 제공하는 개발용 검증 fixture입니다.",
            price, List.of("단지 계약", "지역 운영 파트너"), "공동주택 관리사무소", now, sources, "VERIFIED"));
        var proposal = new PlanningChangeProposalInput("stub-" + run.getId(),
            "초기 고객을 관리사무소 계약 단지로 좁히기", List.of("targetCustomer", "launchArea"),
            mapper.valueToTree("서울 전역의 일반 사용자"), mapper.valueToTree("3개 구의 계약 단지 관리사무소"),
            "초기 영업과 운영 검증 범위를 줄여 근거를 빠르게 확보할 수 있습니다.", sources,
            List.of("타깃 고객", "출시 지역", "영업 채널"), "PENDING");
        var unsigned = new MarketResultIntakeRequest(CONTRACT, run.getId(), run.getInputSnapshotId(), "COMPLETED",
            "local-stub://" + run.getId(), summary, competitors, List.of(proposal), now, "sha256:pending");
        var signed = new MarketResultIntakeRequest(unsigned.contract(), unsigned.moduleRunId(), unsigned.inputSnapshotId(),
            unsigned.status(), unsigned.resultReference(), unsigned.summary(), unsigned.competitors(),
            unsigned.planningChangeProposals(), unsigned.completedAt(), expectedHash(unsigned));
        return intake(signed);
    }

    @Transactional(readOnly = true)
    public MarketResultView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return results.findFirstByProjectIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId)
            .map(this::view).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "시장분석 결과가 아직 없습니다."));
    }

    @Transactional
    public MarketResultView decide(Long ownerId, Long projectId, String proposalId, ProposalDecisionRequest request) {
        requireOwned(ownerId, projectId);
        var proposal = proposals.findByIdAndProjectIdAndDeletedAtIsNull(proposalId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "기획 변경 제안을 찾을 수 없습니다."));
        ProposalDecisionStatus action;
        try { action = ProposalDecisionStatus.valueOf(request.action().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 제안 결정입니다."); }
        try {
            proposal.decide(action, request.modifiedAfter() == null ? null : mapper.writeValueAsString(request.modifiedAfter()));
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage());
        }
        return results.findById(proposal.getModuleRunId()).map(this::view)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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
        if ("COMPLETED".equals(request.status())
                && (request.competitors().isEmpty() || request.planningChangeProposals().isEmpty()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "완료 결과에는 경쟁상품과 기획 변경 제안이 필요합니다.");
        if (!request.resultHash().matches("^sha256:[0-9a-f]{64}$") || !request.resultHash().equals(expectedHash(request)))
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "resultHash가 결과 본문과 일치하지 않습니다.");
        for (var competitor : request.competitors()) {
            if (!competitor.officialUrl().startsWith("https://") || competitor.sourceReferences().isEmpty()
                || "UNVERIFIED".equalsIgnoreCase(competitor.verificationStatus()))
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "경쟁상품에는 검증 상태와 실제 Source Reference가 필요합니다.");
        }
        for (var proposal : request.planningChangeProposals()) {
            String title = proposal.meaningfulTitle().strip();
            if (title.length() < 8 || title.matches("(?i)^v\\d+$") || title.matches("^(변경|제안)\\s*\\d*$")
                || !"PENDING".equals(proposal.decisionStatus()))
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "기획 변경 제안에는 의미 기반 제목과 PENDING 상태가 필요합니다.");
        }
    }

    private MarketResultView view(MarketAnalysisResult result) {
        String currentSnapshot = currentSnapshotId(result.getProjectId());
        boolean stale = snapshotStaleness.isStale(result.getInputSnapshotId(), currentSnapshot);
        List<Competitor> competitors = mapper.readValue(result.getCompetitorsJson(), new TypeReference<List<Competitor>>() {});
        List<ProposalView> proposalViews = proposals.findAllByModuleRunIdAndDeletedAtIsNullOrderByCreatedAtAsc(result.getModuleRunId())
            .stream().map(this::proposalView).toList();
        return new MarketResultView(CONTRACT, result.getModuleRunId(), result.getInputSnapshotId(),
            stale ? "STALE" : result.getStatus(), stale, result.getResultReference(), mapper.readTree(result.getSummaryJson()),
            competitors, proposalViews, result.getCompletedAt(), result.getResultHash());
    }

    private ProposalView proposalView(PlanningChangeProposal value) {
        return new ProposalView(value.getId(), value.getMeaningfulTitle(), readList(value.getAffectedFieldsJson()),
            mapper.readTree(value.getBeforeJson()), mapper.readTree(value.getAfterJson()), value.getReason(),
            mapper.readValue(value.getEvidenceReferencesJson(), new TypeReference<List<SourceReference>>() {}),
            readList(value.getImpactAreasJson()), value.getDecisionStatus().name(),
            value.getModifiedAfterJson() == null ? null : mapper.readTree(value.getModifiedAfterJson()));
    }

    private List<String> readList(String json) { return mapper.readValue(json, new TypeReference<List<String>>() {}); }
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
