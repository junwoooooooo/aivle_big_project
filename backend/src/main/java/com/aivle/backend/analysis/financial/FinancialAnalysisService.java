package com.aivle.backend.analysis.financial;

import static com.aivle.backend.analysis.financial.FinancialModels.*;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.analysis.feasibility.entity.FeasibilityAssessment;
import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.analysis.financial.entity.FinancialAnalysis;
import com.aivle.backend.analysis.financial.entity.FinancialStatus;
import com.aivle.backend.analysis.financial.entity.RevenueModel;
import com.aivle.backend.analysis.financial.repository.FinancialAnalysisRepository;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FinancialAnalysisService {
    private final FinancialAnalysisRepository analyses;
    private final FeasibilityAssessmentRepository feasibility;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final FinancialCalculationService calculator;
    private final FinancialSourceSnapshotService snapshots;
    private final ServicePolicyService servicePolicy;
    private final DomainAuditService audits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SourceResponse source(Long userId, Long projectId) {
        User actor = activeUser(userId);
        Project project = ownedProject(projectId, actor);
        FeasibilityAssessment assessment = completedFeasibility(projectId, actor.getId());
        return new SourceResponse(true, project.getId(), project.getTitle(), assessment.getId(),
            assessment.getStructuredPlan().getId(), assessment.getSourceDocumentVersion().getId(),
            assessment.getSummary(), List.of());
    }

    @Transactional(readOnly = true)
    public List<SummaryResponse> list(Long userId, Long projectId) {
        User actor = activeUser(userId); ownedProject(projectId, actor);
        return analyses.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).stream()
            .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public DetailResponse detail(Long userId, Long projectId, Long analysisId) {
        User actor = activeUser(userId); ownedProject(projectId, actor);
        return detail(analysis(projectId, analysisId));
    }

    @Transactional
    public DetailResponse create(Long userId, Long projectId, Command command, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId); Project project = ownedProjectForUpdate(projectId, actor);
        FeasibilityAssessment assessment = completedFeasibility(projectId, actor.getId());
        validate(command);
        String assumptionsJson = json(command.assumptions());
        String scenariosJson = json(command.scenarios());
        String snapshot = snapshots.capture(project, assessment, Map.of("assumptions", command.assumptions(), "scenarios", command.scenarios()));
        int version = analyses.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).size() + 1;
        FinancialAnalysis entity = analyses.save(FinancialAnalysis.draft(project, actor, assessment,
            assessment.getStructuredPlan(), assessment.getSourceDocumentVersion(), version,
            required(command.title(), 200), "KRW", command.analysisPeriodMonths(), assumptionsJson,
            scenariosJson, snapshot, hash(assumptionsJson + scenariosJson)));
        audit(actor, project, AuditEventType.FINANCIAL_ANALYSIS_CREATED, entity, requestId);
        return detail(entity);
    }

    @Transactional
    public DetailResponse update(Long userId, Long projectId, Long analysisId, Command command, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId); Project project = ownedProjectForUpdate(projectId, actor);
        FinancialAnalysis entity = analysis(projectId, analysisId);
        if (entity.getStatus() == FinancialStatus.COMPLETED) throw new BusinessException(ErrorCode.FINANCIAL_ALREADY_COMPLETED);
        validate(command);
        String assumptionsJson = json(command.assumptions()), scenariosJson = json(command.scenarios());
        entity.updateDraft(required(command.title(), 200), command.analysisPeriodMonths(), assumptionsJson,
            scenariosJson, hash(assumptionsJson + scenariosJson));
        audit(actor, project, AuditEventType.FINANCIAL_ANALYSIS_UPDATED, entity, requestId);
        return detail(entity);
    }

    @Transactional
    public DetailResponse run(Long userId, Long projectId, Long analysisId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId); Project project = ownedProjectForUpdate(projectId, actor);
        FinancialAnalysis entity = analysis(projectId, analysisId);
        if (entity.getStatus() == FinancialStatus.COMPLETED) throw new BusinessException(ErrorCode.FINANCIAL_ALREADY_COMPLETED);
        Assumptions assumptions = fromJson(entity.getAssumptionsJson(), Assumptions.class);
        List<Scenario> scenarios = scenarios(entity.getScenariosJson());
        validate(new Command(entity.getTitle(), entity.getAnalysisPeriodMonths(), assumptions, scenarios));
        CalculationResult result = calculator.calculate(assumptions, entity.getAnalysisPeriodMonths(), scenarios);
        String resultJson = json(result), summaryJson = json(result.summary());
        entity.complete(resultJson, summaryJson, hash(resultJson), LocalDateTime.now(clock));
        project.enterPersonaConfiguration();
        audit(actor, project, AuditEventType.FINANCIAL_ANALYSIS_COMPLETED, entity, requestId);
        return detail(entity);
    }

    @Transactional
    public DetailResponse duplicate(Long userId, Long projectId, Long analysisId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId); Project project = ownedProjectForUpdate(projectId, actor);
        FinancialAnalysis source = analysis(projectId, analysisId);
        int version = analyses.findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).size() + 1;
        FinancialAnalysis copy = analyses.save(FinancialAnalysis.draft(project, actor, source.getFeasibilityAssessment(),
            source.getStructuredPlan(), source.getSourceDocumentVersion(), version, source.getTitle() + " 복제본", "KRW",
            source.getAnalysisPeriodMonths(), source.getAssumptionsJson(), source.getScenariosJson(),
            source.getSourceSnapshotJson(), source.getInputHash()));
        audit(actor, project, AuditEventType.FINANCIAL_ANALYSIS_DUPLICATED, copy, requestId);
        return detail(copy);
    }

    @Transactional
    public void delete(Long userId, Long projectId, Long analysisId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User actor = activeUser(userId); Project project = ownedProjectForUpdate(projectId, actor);
        FinancialAnalysis entity = analysis(projectId, analysisId);
        entity.softDelete(LocalDateTime.now(clock));
        audit(actor, project, AuditEventType.FINANCIAL_ANALYSIS_DELETED, entity, requestId);
    }

    private void validate(Command command) {
        if (command == null || command.assumptions() == null || command.analysisPeriodMonths() == null
            || !List.of(12, 24, 36).contains(command.analysisPeriodMonths())) throw new BusinessException(ErrorCode.FINANCIAL_ASSUMPTION_INVALID);
        Assumptions a = command.assumptions();
        if (a.revenueModel() == null || command.scenarios() == null || command.scenarios().size() != 3)
            throw new BusinessException(ErrorCode.FINANCIAL_SCENARIO_REQUIRED);
        if (!command.scenarios().stream().map(Scenario::code).collect(java.util.stream.Collectors.toSet())
            .containsAll(List.of("CONSERVATIVE", "BASE", "OPTIMISTIC"))) throw new BusinessException(ErrorCode.FINANCIAL_SCENARIO_REQUIRED);
        List<BigDecimal> nonNegative = java.util.stream.Stream.of(a.paymentFeeRate(), a.monthlyChurnRate(),
            a.unitVariableCost(), a.otherVariableCostPerUnit(), a.monthlyLaborCost(), a.monthlyMarketingCost(),
            a.monthlyInfrastructureCost(), a.monthlyRentCost(), a.monthlyOtherFixedCost(), a.initialDevelopmentCost(),
            a.initialEquipmentCost(), a.initialMarketingCost(), a.initialOtherCost()).toList();
        if (nonNegative.stream().anyMatch(value -> value == null || value.signum() < 0)
            || a.monthlyGrowthRate() == null
            || a.monthlyGrowthRate().compareTo(BigDecimal.valueOf(-100)) <= 0
            || a.paymentFeeRate().compareTo(BigDecimal.valueOf(100)) > 0
            || a.monthlyChurnRate().compareTo(BigDecimal.valueOf(100)) > 0) throw new BusinessException(ErrorCode.FINANCIAL_ASSUMPTION_INVALID);
        if (a.revenueModel() != RevenueModel.SUBSCRIPTION && (positiveMissing(a.unitPrice()) || positiveMissing(a.monthlySalesVolume()))) throw new BusinessException(ErrorCode.FINANCIAL_ASSUMPTION_INVALID);
        if (a.revenueModel() != RevenueModel.ONE_TIME && (positiveMissing(a.monthlySubscriptionPrice())
            || missing(a.initialSubscribers()) || missing(a.monthlyNewSubscribers()))) throw new BusinessException(ErrorCode.FINANCIAL_ASSUMPTION_INVALID);
    }

    private boolean positiveMissing(BigDecimal value) { return value == null || value.signum() <= 0; }
    private boolean missing(BigDecimal value) { return value == null || value.signum() < 0; }
    private User activeUser(Long userId) {
        User user = users.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.USER_INACTIVE);
        return user;
    }
    private Project ownedProject(Long projectId, User actor) { return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, actor.getId()).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED)); }
    private Project ownedProjectForUpdate(Long projectId, User actor) { Project project = projects.findByIdForUpdate(projectId).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND)); if (!project.getOwner().getId().equals(actor.getId())) throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED); return project; }
    private FeasibilityAssessment completedFeasibility(Long projectId, Long actorId) {
        FeasibilityAssessment value = feasibility.findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, actorId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_FEASIBILITY_REQUIRED));
        if (value.getStatus() != com.aivle.backend.analysis.feasibility.entity.FeasibilityTypes.AssessmentStatus.COMPLETED) throw new BusinessException(ErrorCode.FINANCIAL_FEASIBILITY_REQUIRED);
        return value;
    }
    private FinancialAnalysis analysis(Long projectId, Long analysisId) { return analyses.findByIdAndProjectIdAndDeletedAtIsNull(analysisId, projectId).orElseThrow(() -> new BusinessException(ErrorCode.FINANCIAL_ANALYSIS_NOT_FOUND)); }
    private void audit(User actor, Project project, AuditEventType type, FinancialAnalysis analysis, String requestId) {
        Map<String, String> metadata = new LinkedHashMap<>(); metadata.put("financialAnalysisId", analysis.getId().toString()); metadata.put("periodMonths", analysis.getAnalysisPeriodMonths().toString()); metadata.put("scenarioCount", "3"); metadata.put("sourceFeasibilityAssessmentId", analysis.getFeasibilityAssessment().getId().toString());
        audits.record(actor.getId(), project.getId(), type, "FinancialAnalysis", analysis.getId(), requestId, metadata);
    }
    private SummaryResponse summary(FinancialAnalysis entity) { return new SummaryResponse(entity.getId(), entity.getTitle(), entity.getStatus(), entity.getAnalysisPeriodMonths(), entity.getVersionNumber(), entity.getCompletedAt(), entity.getUpdatedAt(), entity.getSummaryJson()); }
    private DetailResponse detail(FinancialAnalysis entity) { return new DetailResponse(summary(entity), fromJson(entity.getAssumptionsJson(), Assumptions.class), scenarios(entity.getScenariosJson()), entity.getSourceSnapshotJson(), entity.getResultJson(), entity.getSummaryJson(), entity.getFeasibilityAssessment().getId()); }
    private String required(String value, int max) { if (value == null || value.isBlank() || value.length() > max) throw new BusinessException(ErrorCode.FINANCIAL_ASSUMPTION_INVALID); return value.trim(); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JacksonException ex) { throw new IllegalStateException("financial JSON serialization failed", ex); } }
    private <T> T fromJson(String value, Class<T> type) { try { return objectMapper.readValue(value, type); } catch (JacksonException ex) { throw new BusinessException(ErrorCode.FINANCIAL_SOURCE_INVALID); } }
    private List<Scenario> scenarios(String value) { try { return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, Scenario.class)); } catch (JacksonException ex) { throw new BusinessException(ErrorCode.FINANCIAL_SOURCE_INVALID); } }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }

    public record Command(String title, Integer analysisPeriodMonths, Assumptions assumptions, List<Scenario> scenarios) { }
    public record SourceResponse(boolean ready, Long projectId, String projectTitle, Long feasibilityAssessmentId, Long structuredPlanId, Long sourceDocumentVersionId, String feasibilitySummary, List<MissingField> missingFields) { }
    public record SummaryResponse(Long id, String title, FinancialStatus status, Integer analysisPeriodMonths, Integer versionNumber, LocalDateTime completedAt, LocalDateTime updatedAt, String summaryJson) { }
    public record DetailResponse(SummaryResponse summary, Assumptions assumptions, List<Scenario> scenarios, String sourceSnapshotJson, String resultJson, String summaryJson, Long feasibilityAssessmentId) { }
}
