package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.contract.LegalSourcePipelineInput;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Slf4j
public class LegalPrecheckService {
    static final String REGISTRY_VERSION = "legal-registry-v1";
    static final String PROMPT_VERSION = "legal-precheck-v1";
    static final String SCHEMA_VERSION = "1.0";
    private final ProjectRepository projects; private final IdeaSourceRepository sources; private final IdeaOriginVersionRepository origins;
    private final LegalPrecheckRunRepository runs; private final LegalPrecheckVersionRepository versions;
    private final LegalGuardrailSetRepository guardrails; private final TaskResultRepository taskResults;
    private final TaskRunService taskRuns; private final CanonicalInputHasher hasher;
    private final IdeaOriginService ideaOrigins; private final ObjectMapper mapper;

    public LegalPrecheckService(ProjectRepository projects, IdeaSourceRepository sources, IdeaOriginVersionRepository origins,
            LegalPrecheckRunRepository runs, LegalPrecheckVersionRepository versions,
            LegalGuardrailSetRepository guardrails, TaskResultRepository taskResults,
            TaskRunService taskRuns, CanonicalInputHasher hasher, IdeaOriginService ideaOrigins, ObjectMapper mapper) {
        this.projects=projects; this.sources=sources; this.origins=origins; this.runs=runs; this.versions=versions;
        this.guardrails=guardrails; this.taskResults=taskResults; this.taskRuns=taskRuns;
        this.hasher=hasher; this.ideaOrigins=ideaOrigins; this.mapper=mapper;
    }

    @Transactional
    public StartView start(Long ownerId, Long projectId) {
        return start(ownerId, projectId, false);
    }

    @Transactional
    public StartView refreshSources(Long ownerId, Long projectId) {
        return start(ownerId, projectId, true);
    }

    private StartView start(Long ownerId, Long projectId, boolean forceNewTerminalRun) {
        Project project = owned(ownerId, projectId); IdeaOriginVersion origin = currentOrigin(projectId);
        LegalSourcePipelineInput input = legalInput(origin);
        String inputJson = input.toJson(mapper); String inputHash = hasher.hash(TaskType.IDEA_LEGAL_PRECHECK, SCHEMA_VERSION, "ko-KR", inputJson);
        LegalPrecheckRun existing = runs.findTopByProjectIdAndIdeaOriginVersionIdAndInputSnapshotHashAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, origin.getId(), inputHash).orElse(null);
        if (existing != null) {
            boolean active = existing.getState() == LegalPrecheckRun.State.QUEUED
                || existing.getState() == LegalPrecheckRun.State.RUNNING;
            boolean configurationFailure = existing.getState() == LegalPrecheckRun.State.FAILED
                && "AI_CONFIGURATION_INVALID".equals(existing.getErrorCode());
            if (active || (!forceNewTerminalRun && !configurationFailure)) return startView(existing);
        }
        String key = "legal-origin-" + origin.getId() + "-" + UUID.randomUUID();
        TaskRun task = taskRuns.create(ownerId, projectId, TaskType.IDEA_LEGAL_PRECHECK, "IDEA_ORIGIN_VERSION",
            origin.getId().toString(), inputJson, inputHash, key, UUID.randomUUID().toString(), 3);
        return startView(runs.save(LegalPrecheckRun.create(project, origin, task, inputHash,
            REGISTRY_VERSION, PROMPT_VERSION, SCHEMA_VERSION)));
    }

    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId); IdeaOriginVersion currentOrigin = sources.findCurrent(projectId)
            .flatMap(source -> origins.findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.CONFIRMED)).orElse(null);
        LegalPrecheckRun run = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (run == null) return new CurrentView(null, null, false);
        synchronize(run);
        LegalPrecheckVersion version = versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        boolean stale = currentOrigin == null || !run.getIdeaOriginVersion().getId().equals(currentOrigin.getId());
        if (!stale) {
            String currentInputJson = legalInput(currentOrigin).toJson(mapper);
            String currentHash = hasher.hash(TaskType.IDEA_LEGAL_PRECHECK, SCHEMA_VERSION, "ko-KR", currentInputJson);
            stale = !run.getInputSnapshotHash().equals(currentHash);
        }
        return new CurrentView(runView(run), version == null ? null : versionView(version), stale);
    }

    @Transactional
    public IdeaOriginService.WorkspaceView applyAnswers(Long ownerId, Long projectId, Long originVersionId) {
        return ideaOrigins.applyLegalAnswers(ownerId, projectId, originVersionId);
    }

    @Transactional
    public IdeaOriginService.WorkspaceView acceptRevision(Long ownerId, Long projectId, Long versionId, int index) {
        LegalPrecheckVersion version = versions.findById(versionId)
            .filter(value -> value.getProject().getId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        JsonNode suggestions = mapper.readTree(version.getRevisionSuggestionsJson());
        if (!suggestions.isArray() || index < 0 || index >= suggestions.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        JsonNode suggestion = suggestions.get(index);
        return ideaOrigins.acceptLegalRevision(ownerId, projectId, version.getIdeaOriginVersion().getId(),
            suggestion.path("targetField").asText(), suggestion.path("proposedValue").asText());
    }

    @Transactional
    public RevisionApplyView acceptRevisionsAndRestart(Long ownerId, Long projectId, Long versionId,
            List<Integer> indexes) {
        LegalPrecheckVersion version = versions.findById(versionId)
            .filter(value -> value.getProject().getId().equals(projectId) && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        JsonNode suggestions = mapper.readTree(version.getRevisionSuggestionsJson());
        if (!suggestions.isArray() || indexes == null || indexes.isEmpty() || indexes.size() > 50)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        List<Integer> selected = indexes.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (selected.size() != indexes.size() || selected.stream().anyMatch(index -> index < 0 || index >= suggestions.size()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        List<IdeaOriginService.LegalRevision> revisions = new ArrayList<>();
        for (Integer index : selected) {
            JsonNode suggestion = suggestions.get(index);
            revisions.add(new IdeaOriginService.LegalRevision(
                suggestion.path("targetField").asText(), suggestion.path("proposedValue").asText()));
        }
        IdeaOriginService.WorkspaceView origin = ideaOrigins.acceptLegalRevisions(ownerId, projectId,
            version.getIdeaOriginVersion().getId(), revisions);
        StartView precheck = start(ownerId, projectId);
        return new RevisionApplyView(origin, precheck, selected.size());
    }

    @Transactional
    public RevisionApplyView applyAnswersAndRestart(Long ownerId, Long projectId, Long originVersionId) {
        IdeaOriginService.WorkspaceView origin = ideaOrigins.applyLegalAnswers(ownerId, projectId, originVersionId);
        return new RevisionApplyView(origin, start(ownerId, projectId), 0);
    }

    private void synchronize(LegalPrecheckRun run) {
        TaskRun task = run.getTaskRun();
        if (task.getState() == TaskRunState.QUEUED || task.getState() == TaskRunState.READY) { run.queued(); runs.save(run); return; }
        if (task.getState() == TaskRunState.RUNNING) { run.running(); runs.save(run); return; }
        if (task.getState() == TaskRunState.FAILED || task.getState() == TaskRunState.TIMED_OUT || task.getState() == TaskRunState.CANCELLED) {
            if (run.getState() != LegalPrecheckRun.State.FAILED) { log.warn("Legal precheck task failed projectId={} runId={} taskRunId={} errorCode={} retryable={}",run.getProject().getId(),run.getId(),task.getId(),task.getLastErrorCode(),task.isRetryable()); run.fail(task.getLastErrorCode()); runs.save(run); }
            return;
        }
        if (task.getState() != TaskRunState.SUCCEEDED || versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).isPresent()) return;
        TaskResult taskResult = task.getFinalResultId() == null ? null : taskResults.findById(task.getFinalResultId()).orElse(null);
        if (taskResult == null) return;
        materialize(run, mapper.readTree(taskResult.getResultJson())); run.succeed(); runs.save(run);
    }

    private void materialize(LegalPrecheckRun run, JsonNode result) {
        JsonNode findings = result.path("findings"); JsonNode evidence = result.path("evidence");
        JsonNode requiredInputs = result.path("requiredUserInputs"); String sourceStatus = result.path("sourceStatus").asText();
        if (!"SOURCE_COMPLETE".equals(sourceStatus)) log.warn("Legal precheck source incomplete projectId={} runId={} sourceStatus={} registryVersion={}",run.getProject().getId(),run.getId(),sourceStatus,result.path("registryVersion").asText());
        Set<String> resolvedCategories = acceptedLegalCategories(run.getIdeaOriginVersion());
        GuardrailDraft guardrail = buildGuardrails(findings, evidence, resolvedCategories);
        ArrayNode revisions = revisionSuggestions(evidence, result.path("routes"), resolvedCategories);
        LegalPrecheckVersion.Status status = decide(sourceStatus, requiredInputs, guardrail, evidence, resolvedCategories);
        boolean allowed = status == LegalPrecheckVersion.Status.PASS || status == LegalPrecheckVersion.Status.PASS_WITH_CONDITIONS;
        boolean verified = evidence.isArray() && !evidence.isEmpty() && allOfficial(evidence);
        String summary = summary(status, sourceStatus, evidence.size(), requiredInputs.size());
        int number = Math.toIntExact(versions.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1);
        LegalPrecheckVersion version = versions.save(LegalPrecheckVersion.create(run.getProject(), run.getIdeaOriginVersion(), run,
            number, status, sourceStatus, summary, findings.toString(), evidence.toString(), requiredInputs.toString(),
            revisions.toString(), allowed, verified, result.path("registryVersion").asText()));
        int guardrailNumber = Math.toIntExact(guardrails.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1);
        guardrails.save(LegalGuardrailSet.create(run.getProject(), version, run, guardrailNumber,
            guardrail.hard().toString(), guardrail.prohibited().toString(), guardrail.conditional().toString(),
            guardrail.disclosures().toString(), guardrail.controls().toString()));
        ideaOrigins.addLegalQuestions(run.getProject(), run.getIdeaOriginVersion(), requiredInputs);
    }

    private GuardrailDraft buildGuardrails(JsonNode findings, JsonNode evidence, Set<String> resolvedCategories) {
        LinkedHashSet<String> hard=new LinkedHashSet<>(), prohibited=new LinkedHashSet<>(), conditional=new LinkedHashSet<>(), disclosures=new LinkedHashSet<>(), controls=new LinkedHashSet<>();
        if (evidence.isArray()) for (JsonNode item : evidence) {
            String summary=item.path("plainSummary").asText(); String combined=summary+" "+item.path("title").asText();
            switch (item.path("role").asText()) {
                case "REQUIREMENT", "SCOPE" -> hard.add(summary);
                case "SANCTION" -> controls.add(summary);
                default -> conditional.add(summary);
            }
            boolean prohibitedText = containsAny(combined, "금지", "하여서는 아니", "할 수 없다");
            if (prohibitedText && resolvedCategories.contains(item.path("category").asText()))
                conditional.add("사용자가 수락한 사업 범위 제한: "+summary);
            else if (prohibitedText) prohibited.add(summary);
            if (containsAny(combined, "표시", "고지", "공개", "처리방침")) disclosures.add(summary);
            if (containsAny(combined, "허가", "신고", "등록", "인증", "보관", "안전")) controls.add(summary);
        }
        if (findings.isArray()) for (JsonNode finding : findings)
            if ("POSSIBLE".equals(finding.path("applicability").asText())) conditional.add(finding.path("summary").asText());
        return new GuardrailDraft(array(hard),array(prohibited),array(conditional),array(disclosures),array(controls));
    }

    LegalPrecheckVersion.Status decide(String sourceStatus, JsonNode questions, GuardrailDraft guardrail,
            JsonNode evidence, Set<String> resolvedCategories) {
        if (questions.isArray() && !questions.isEmpty()) return LegalPrecheckVersion.Status.INSUFFICIENT_INFORMATION;
        if (!evidence.isArray() || evidence.isEmpty()) return LegalPrecheckVersion.Status.EXPERT_REVIEW_REQUIRED;
        if (hasHardProhibition(evidence, resolvedCategories)) return LegalPrecheckVersion.Status.PROHIBITED;
        if (!guardrail.prohibited().isEmpty()) return LegalPrecheckVersion.Status.REVISION_REQUIRED;
        if ("REGISTRY_GAP".equals(sourceStatus)) return LegalPrecheckVersion.Status.EXPERT_REVIEW_REQUIRED;
        if ("SOURCE_PARTIAL".equals(sourceStatus)) return LegalPrecheckVersion.Status.PASS_WITH_CONDITIONS;
        if (!guardrail.hard().isEmpty() || !guardrail.conditional().isEmpty() || !guardrail.disclosures().isEmpty() || !guardrail.controls().isEmpty())
            return LegalPrecheckVersion.Status.PASS_WITH_CONDITIONS;
        return LegalPrecheckVersion.Status.PASS;
    }

    ArrayNode revisionSuggestions(JsonNode evidence, JsonNode routes, Set<String> resolvedCategories) {
        ArrayNode values=mapper.createArrayNode(); if (!evidence.isArray()) return values;
        Map<String,List<JsonNode>> byCategory=new LinkedHashMap<>();
        for(JsonNode item:evidence){String category=item.path("category").asText();String summary=item.path("plainSummary").asText();
            if(resolvedCategories.contains(category)||!Set.of("REQUIREMENT","SCOPE").contains(item.path("role").asText())
                ||!containsAny(summary,"금지","하여서는 아니","할 수 없다"))continue;
            byCategory.computeIfAbsent(category,ignored->new ArrayList<>()).add(item);
        }
        for(var entry:byCategory.entrySet()){
            LinkedHashSet<String> summaries=new LinkedHashSet<>(),reasons=new LinkedHashSet<>(),originEvidence=new LinkedHashSet<>();
            ArrayNode evidenceIds=mapper.createArrayNode();
            for(JsonNode item:entry.getValue()){summaries.add(item.path("plainSummary").asText());reasons.add(item.path("whyRelevant").asText());
                originEvidence.add(routeEvidence(routes,item.path("routeId").asText()));evidenceIds.add(item.path("evidenceId").asText());}
            ObjectNode value=values.addObject();value.put("category",entry.getKey());
            value.put("targetField","legal."+entry.getKey().toLowerCase(Locale.ROOT));
            value.put("reason",String.join(" ",reasons));value.put("originEvidence",String.join(" · ",originEvidence));
            value.put("proposedValue",entry.getKey()+" 관련 금지·제한을 피하도록 사업 범위를 다음 조건으로 확정합니다: "+String.join(" ",summaries));
            value.put("evidenceId",evidenceIds.isEmpty()?"":evidenceIds.get(0).asText());value.set("evidenceIds",evidenceIds);
        }return values;
    }
    private String routeEvidence(JsonNode routes,String routeId){if(routes.isArray())for(JsonNode route:routes)if(routeId.equals(route.path("routeId").asText())){JsonNode quotes=route.path("evidenceQuotes");if(quotes.isArray()&&!quotes.isEmpty())return quotes.get(0).asText();}return "확정된 Idea Origin의 관련 사업 설명";}

    private List<Map<String,Object>> confirmedFacts(IdeaOriginVersion origin) {
        List<Map<String,Object>> values=new ArrayList<>(); JsonNode node=mapper.readTree(origin.getConfirmedValuesJson());
        if (node.isObject()) for (String key:node.propertyNames()) {
            JsonNode item=node.get(key); Map<String,Object> value=new LinkedHashMap<>(); value.put("key",key);
            value.put("value",mapper.convertValue(item.path("value"),Object.class)); value.put("source",item.path("source").asText("사용자 확인")); values.add(value);
        } return values;
        }
    private LegalSourcePipelineInput legalInput(IdeaOriginVersion origin){return LegalSourcePipelineInput.create(origin.getSnapshotJson(),"idea-origin-v"+origin.getVersionNumber(),"FULL",List.of(),confirmedFacts(origin),REGISTRY_VERSION,PROMPT_VERSION,SCHEMA_VERSION);}
    private boolean allOfficial(JsonNode evidence) { for(JsonNode item:evidence) if(!item.path("lawUrl").asText().startsWith("https://www.law.go.kr/"))return false; return true; }
    private Set<String> acceptedLegalCategories(IdeaOriginVersion origin){Set<String> values=new HashSet<>();JsonNode confirmed=mapper.readTree(origin.getConfirmedValuesJson());
        if(confirmed.isObject())for(String key:confirmed.propertyNames())if(key.startsWith("legal.")&&key.length()>6)values.add(key.substring(6).toUpperCase(Locale.ROOT));return values;}
    private boolean hasHardProhibition(JsonNode evidence,Set<String> resolvedCategories){for(JsonNode item:evidence){if(resolvedCategories.contains(item.path("category").asText())||!Set.of("REQUIREMENT","SCOPE").contains(item.path("role").asText()))continue;String value=item.path("plainSummary").asText();if(value.contains("전면 금지")||(value.contains("누구든지")&&value.contains("하여서는 아니")))return true;}return false;}
    private boolean containsAny(String value,String...tokens){for(String token:tokens)if(value.contains(token))return true;return false;}
    private ArrayNode array(Collection<String> values){ArrayNode result=mapper.createArrayNode();values.stream().filter(v->v!=null&&!v.isBlank()).forEach(result::add);return result;}
    private String summary(LegalPrecheckVersion.Status status,String sourceStatus,int evidence,int questions){return switch(status){
        case PASS->"현재 입력과 확인된 공식 근거에서 Concept 진행을 막는 조건을 찾지 못했습니다.";
        case PASS_WITH_CONDITIONS->"확인된 공식 근거 "+evidence+"건의 조건을 Guardrail로 적용하면 진행할 수 있습니다.";
        case REVISION_REQUIRED->"금지 또는 제한 가능성이 있는 내용을 수정한 뒤 다시 검토해야 합니다.";
        case INSUFFICIENT_INFORMATION->"법률 적용 범위를 확정하려면 추가 질문 "+questions+"개에 답해야 합니다.";
        case EXPERT_REVIEW_REQUIRED->sourceStatus+" 상태이므로 전문가 또는 공식 Source 추가 확인이 필요합니다.";
        case PROHIBITED->"현재 형태로는 진행할 수 없습니다.";};}
    private Project owned(Long ownerId,Long projectId){return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private IdeaOriginVersion currentOrigin(Long projectId){IdeaSource source=sources.findCurrent(projectId).orElseThrow(()->new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));return origins.findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(projectId,source.getId(),IdeaOriginVersion.State.CONFIRMED).orElseThrow(()->new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));}
    private StartView startView(LegalPrecheckRun run){return new StartView(run.getId(),run.getTaskRun().getId(),run.getState().name(),run.getTaskRun().isRetryable(),run.getIdeaOriginVersion().getId(),run.getInputSnapshotHash());}
    private RunView runView(LegalPrecheckRun run){return new RunView(run.getId(),run.getTaskRun().getId(),run.getState().name(),run.getTaskRun().isRetryable(),run.getErrorCode(),run.getIdeaOriginVersion().getId(),run.getInputSnapshotHash(),run.getRegistryVersion(),run.getPromptVersion(),run.getSchemaVersion());}
    private VersionView versionView(LegalPrecheckVersion value){LegalGuardrailSet set=guardrails.findByLegalPrecheckVersionIdAndDeletedAtIsNull(value.getId()).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));return new VersionView(value.getId(),value.getVersionNumber(),value.getIdeaOriginVersion().getId(),value.getStatus().name(),value.getSourceStatus(),value.getSummary(),mapper.readTree(value.getFindingsJson()),mapper.readTree(value.getEvidenceJson()),mapper.readTree(value.getQuestionsJson()),mapper.readTree(value.getRevisionSuggestionsJson()),value.isConceptBuilderAllowed(),value.isSourceVerified(),value.getRegistryVersion(),new GuardrailView(set.getId(),set.getVersionNumber(),mapper.readTree(set.getHardConstraintsJson()),mapper.readTree(set.getProhibitedPatternsJson()),mapper.readTree(set.getConditionalConstraintsJson()),mapper.readTree(set.getRequiredDisclosuresJson()),mapper.readTree(set.getRequiredOperationalControlsJson())));}

    record GuardrailDraft(ArrayNode hard,ArrayNode prohibited,ArrayNode conditional,ArrayNode disclosures,ArrayNode controls){}
    public record StartView(Long runId,String taskRunId,String state,boolean retryable,Long ideaOriginVersionId,String inputSnapshotHash){}
    public record RunView(Long id,String taskRunId,String state,boolean retryable,String errorCode,Long ideaOriginVersionId,String inputSnapshotHash,String registryVersion,String promptVersion,String schemaVersion){}
    public record GuardrailView(Long id,int versionNumber,JsonNode hardConstraints,JsonNode prohibitedPatterns,JsonNode conditionalConstraints,JsonNode requiredDisclosures,JsonNode requiredOperationalControls){}
    public record VersionView(Long id,int versionNumber,Long ideaOriginVersionId,String status,String sourceStatus,String summary,JsonNode findings,JsonNode evidence,JsonNode requiredUserInputs,JsonNode revisionSuggestions,boolean conceptBuilderAllowed,boolean sourceVerified,String registryVersion,GuardrailView guardrails){}
    public record CurrentView(RunView run,VersionView version,boolean stale){}
    public record RevisionApplyView(IdeaOriginService.WorkspaceView origin,StartView precheck,int appliedSuggestionCount){}
}
