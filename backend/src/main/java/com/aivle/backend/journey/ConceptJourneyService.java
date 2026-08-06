package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

@Service
@Slf4j
public class ConceptJourneyService {
    private final ProjectRepository projects; private final IdeaVersionRepository ideaVersions;
    private final IdeaOriginVersionRepository ideaOrigins; private final LegalPrecheckVersionRepository legalPrechecks;
    private final LegalGuardrailSetRepository guardrailSets; private final ConceptEligibilityBatchRepository eligibilityBatches;
    private final ConceptDraftRepository conceptDrafts;
    private final ConceptGenerationRunRepository generationRuns; private final ConceptVersionRepository concepts;
    private final QuickAssessmentRunRepository quickRuns; private final QuickAssessmentRepository quickAssessments;
    private final ShortlistDecisionRepository shortlists; private final DetailedAnalysisRunRepository detailedRuns;
    private final DetailedAnalysisRepository detailedAnalyses; private final JourneyFinancialAnalysisRepository financials;
    private final ConceptSelectionRepository selections; private final TaskRunService taskRuns; private final InternalAiExecutionClient ai;
    private final CanonicalInputHasher hasher; private final ObjectMapper mapper; private final ConceptJourneyPersistenceService persistence;
    private final TaskExecutor conceptEligibilityExecutor;
    @Value("${app.journey.concept.target-count:3}") private int targetEligibleCount;
    @Value("${app.journey.concept.max-replacement-rounds:2}") private int maxReplacementRounds;
    @Value("${app.journey.concept.max-inspected-candidates:9}") private int maxInspectedCandidates;

    public ConceptJourneyService(ProjectRepository projects, IdeaVersionRepository ideaVersions,
            IdeaOriginVersionRepository ideaOrigins, LegalPrecheckVersionRepository legalPrechecks,
            LegalGuardrailSetRepository guardrailSets, ConceptEligibilityBatchRepository eligibilityBatches,
            ConceptDraftRepository conceptDrafts,
            ConceptGenerationRunRepository generationRuns, ConceptVersionRepository concepts,
            QuickAssessmentRunRepository quickRuns, QuickAssessmentRepository quickAssessments,
            ShortlistDecisionRepository shortlists, DetailedAnalysisRunRepository detailedRuns,
            DetailedAnalysisRepository detailedAnalyses, JourneyFinancialAnalysisRepository financials,
            ConceptSelectionRepository selections, TaskRunService taskRuns, InternalAiExecutionClient ai,
            CanonicalInputHasher hasher, ObjectMapper mapper, ConceptJourneyPersistenceService persistence,
            @Qualifier("conceptEligibilityExecutor") TaskExecutor conceptEligibilityExecutor) {
        this.projects=projects; this.ideaVersions=ideaVersions; this.ideaOrigins=ideaOrigins; this.legalPrechecks=legalPrechecks;
        this.guardrailSets=guardrailSets; this.eligibilityBatches=eligibilityBatches; this.conceptDrafts=conceptDrafts;
        this.generationRuns=generationRuns; this.concepts=concepts;
        this.quickRuns=quickRuns; this.quickAssessments=quickAssessments; this.shortlists=shortlists; this.detailedRuns=detailedRuns;
        this.detailedAnalyses=detailedAnalyses; this.financials=financials; this.selections=selections; this.taskRuns=taskRuns;
        this.ai=ai; this.hasher=hasher; this.mapper=mapper; this.persistence=persistence;
        this.conceptEligibilityExecutor=conceptEligibilityExecutor;
    }

    public BatchView generate(Long ownerId, Long projectId) {
        Context context=context(ownerId,projectId,true); String snapshotHash=eligibilityInputHash(context);
        ConceptEligibilityBatch existing=eligibilityBatches.findTopByProjectIdAndInputSnapshotHashAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,snapshotHash).orElse(null);
        if(existing!=null&&!existing.allowsManualRestart())return batchView(existing,false);
        ConceptEligibilityBatch batch=eligibilityBatches.save(ConceptEligibilityBatch.create(context.project(),context.origin(),context.guardrail(),snapshotHash,"concept-eligibility-v1","1.0",targetEligibleCount,maxReplacementRounds,maxInspectedCandidates));
        conceptEligibilityExecutor.execute(()->runEligibility(ownerId,projectId,batch.getId()));
        return batchView(batch,false);
    }

    private void runEligibility(Long ownerId,Long projectId,Long batchId) {
        ConceptEligibilityBatch batch=eligibilityBatches.findById(batchId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ConceptGenerationRun run=null;
        try {
            Context context=context(ownerId,projectId,true);
            if(!batch.getInputSnapshotHash().equals(eligibilityInputHash(context))){batch.needsInput("[\"CONCEPT_INPUT_BECAME_STALE\"]");eligibilityBatches.save(batch);return;}
            run=generationRuns.save(ConceptGenerationRun.pending(context.project(),context.idea()));
            List<ConceptDraft> accepted=new ArrayList<>(); List<String> negatives=new ArrayList<>(); int sequence=0;
            for(int round=0;round<=batch.getMaxReplacementRounds()&&accepted.size()<batch.getTargetEligibleCount()&&sequence<batch.getMaxInspectedCandidates();round++){
                int desired=Math.min(batch.getTargetEligibleCount()-accepted.size(),batch.getMaxInspectedCandidates()-sequence);
                batch.stage(ConceptEligibilityBatch.State.GENERATING,round);eligibilityBatches.save(batch);
                String generationInput=conceptGenerationInput(context,desired,round,negatives,accepted);
                TaskRun generationTask=createTask(ownerId,context.project(),TaskType.CONCEPT_GENERATION,"CONCEPT_BATCH",batch.getId().toString(),generationInput);
                if(round==0){run.start(generationTask);generationRuns.save(run);}
                JsonNode generated=execute(generationTask,value->validateConceptGeneration(value,desired));
                List<LegalCandidate> legalCandidates=new ArrayList<>();
                for(JsonNode candidate:generated.get("concepts")){
                    if(sequence>=batch.getMaxInspectedCandidates())break; sequence++;
                    batch.stage(ConceptEligibilityBatch.State.VALIDATING_ORIGIN,round);eligibilityBatches.save(batch);
                    Validation origin=originIntegrity(candidate,context.origin()); String fingerprint=fingerprint(candidate);
                    if(conceptDrafts.findByBatchIdAndDeletedAtIsNullOrderBySequenceNumber(batch.getId()).stream().anyMatch(value->value.getFingerprint().equals(fingerprint)))origin=new Validation(false,List.of("DUPLICATE_CONCEPT_STRUCTURE"),List.of("concept.structure"));
                    ConceptDraft draft=conceptDrafts.save(ConceptDraft.create(batch,generationTask,round,sequence,batch.getInputSnapshotHash(),"concept-eligibility-v1","1.0",fingerprint,candidate.toString(),origin.pass()?ConceptDraft.OriginStatus.PASS:ConceptDraft.OriginStatus.FAIL_ORIGIN,mapper.valueToTree(origin.reasons()).toString()));
                    if(!origin.pass()){batch.inspected(false);negatives.addAll(origin.reasons());continue;}
                    legalCandidates.add(new LegalCandidate("candidate-"+draft.getId(),draft,(ObjectNode)candidate));
                }
                if(!legalCandidates.isEmpty()){
                    batch.stage(ConceptEligibilityBatch.State.VALIDATING_LEGAL,round);eligibilityBatches.save(batch);
                    Set<String> expectedKeys=legalCandidates.stream().map(LegalCandidate::candidateKey).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    String legalInput=conceptLegalBatchInput(context,legalCandidates);
                    TaskRun legalTask=createTask(ownerId,context.project(),TaskType.CONCEPT_LEGAL_VALIDATION,"CONCEPT_ELIGIBILITY_ROUND",batch.getId()+":"+round,legalInput);
                    JsonNode legalBatch=execute(legalTask,value->validateConceptLegalBatch(value,expectedKeys));
                    Map<String,JsonNode> validations=new HashMap<>();
                    legalBatch.get("validations").forEach(value->validations.put(value.get("candidateKey").asText(),value));
                    for(LegalCandidate value:legalCandidates){
                        JsonNode legal=validations.get(value.candidateKey()); boolean pass="PASS".equals(legal.get("status").asText());
                        value.candidate().set("legalTrace",legal.get("legalTrace"));
                        value.draft().legal(pass?ConceptDraft.LegalStatus.PASS:ConceptDraft.LegalStatus.FAIL_LEGAL,legal.get("reasons").toString(),legal.get("violatedStructureKeys").toString(),pass?null:String.join(" ",strings(legal.get("reasons"))),value.candidate().toString());conceptDrafts.save(value.draft());
                        batch.inspected(pass); if(pass)accepted.add(value.draft());else{negatives.addAll(strings(legal.get("reasons")));negatives.addAll(strings(legal.get("violatedStructureKeys")));}
                    }
                }
            }
            if(accepted.size()==batch.getTargetEligibleCount()){persistence.publishEligible(batch,run,accepted);batch.complete();}
            else{ArrayNode needs=mapper.createArrayNode();new LinkedHashSet<>(negatives).forEach(needs::add);batch.needsInput(needs.toString());run.fail("CONCEPT_ELIGIBLE_COUNT_NOT_REACHED");}
            eligibilityBatches.save(batch);
        }catch(ExecutionFailure failure){String code=conceptFailureCode(failure);batch.fail(code,failure.retryable());eligibilityBatches.save(batch);if(run!=null)persistence.failGeneration(run.getId(),code);log.warn("Concept eligibility provider failure projectId={} batchId={} code={} providerCode={} reason={} retryable={}",projectId,batchId,code,failure.code(),failure.reason(),failure.retryable());}
        catch(RuntimeException failure){batch.fail("AI_RESULT_INVALID",false);eligibilityBatches.save(batch);if(run!=null)persistence.failGeneration(run.getId(),"AI_RESULT_INVALID");log.warn("Concept eligibility contract failure projectId={} batchId={} code=AI_RESULT_INVALID",projectId,batchId,failure);}
    }

    public List<ConceptView> concepts(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);ConceptEligibilityBatch batch=eligibilityBatches.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if(batch==null||batch.getState()!=ConceptEligibilityBatch.State.COMPLETED||context.origin()==null||context.guardrail()==null||!batch.getInputSnapshotHash().equals(eligibilityInputHash(context)))return List.of();
        return concepts.findByEligibilityBatchIdAndEligibilityStatusAndDeletedAtIsNullOrderByConceptDisplayOrder(batch.getId(),"ELIGIBLE").stream().map(this::conceptView).toList();
    }

    public BatchView currentBatch(Long ownerId,Long projectId){Context context=context(ownerId,projectId,false);ConceptEligibilityBatch batch=eligibilityBatches.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);if(batch==null)return null;boolean stale=context.origin()==null||context.guardrail()==null||!batch.getInputSnapshotHash().equals(eligibilityInputHash(context));return batchView(batch,stale);}

    public QuickView quick(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,true); List<ConceptVersion> candidates=requireConcepts(context);
        QuickAssessmentRun current=quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(current!=null&&current.getState()==ConceptAiRunBase.State.SUCCEEDED) return quickView(current);
        if(current!=null&&(current.getState()==ConceptAiRunBase.State.PENDING||current.getState()==ConceptAiRunBase.State.RUNNING)) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        QuickAssessmentRun run=quickRuns.save(QuickAssessmentRun.pending(context.project(),context.idea()));
        TaskRun task=createTask(ownerId,context.project(),TaskType.QUICK_ASSESSMENT,"IDEA_VERSION",context.idea().getId().toString(),conceptsInput("concepts",candidates));
        run.start(task); quickRuns.save(run);
        try { JsonNode result=execute(task,value->validateQuick(value,candidates)); persistence.completeQuick(run.getId(),result); }
        catch(ExecutionFailure failure){ persistence.failQuick(run.getId(),failure.reason()); throw publicFailure(failure); }
        catch(RuntimeException failure){ persistence.failQuick(run.getId(),"AI_RESULT_INVALID"); throw normalized(failure); }
        return currentQuick(ownerId,projectId);
    }

    public QuickView currentQuick(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::quickView).orElse(null);
    }

    public ShortlistView shortlist(Long ownerId,Long projectId,ShortlistRequest request) {
        Context context=context(ownerId,projectId,false); List<Long> ids=distinctIds(request.conceptVersionIds());
        QuickAssessmentRun quick=quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(quick==null||quick.getState()!=ConceptAiRunBase.State.SUCCEEDED) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        if(ids.isEmpty()||concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(ids,projectId,context.idea().getId()).size()!=ids.size()) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ShortlistDecision saved=shortlists.save(ShortlistDecision.create(context.project(),context.idea(),mapper.valueToTree(ids).toString(),trim(request.reason(),2000)));
        return shortlistView(saved);
    }

    public ShortlistView currentShortlist(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::shortlistView).orElse(null);
    }

    public DetailedView detailed(Long ownerId,Long projectId,DetailedRequest request) {
        Context context=context(ownerId,projectId,true);
        ShortlistDecision shortlist=shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));
        List<Long> ids=idList(shortlist.getSelectedConceptVersionIdsJson());
        List<ConceptVersion> selected=concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(ids,projectId,context.idea().getId());
        validateFinancialInputs(request.financials(),ids);
        DetailedAnalysisRun current=detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(current!=null&&current.getState()==ConceptAiRunBase.State.SUCCEEDED) {
            Set<Long> completedIds=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(current.getId()).stream()
                .map(value->value.getConceptVersion().getId()).collect(java.util.stream.Collectors.toSet());
            if(completedIds.equals(new HashSet<>(ids))) return detailedView(current);
        }
        if(current!=null&&(current.getState()==ConceptAiRunBase.State.PENDING||current.getState()==ConceptAiRunBase.State.RUNNING)) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        DetailedAnalysisRun run=detailedRuns.save(DetailedAnalysisRun.pending(context.project(),context.idea()));
        TaskRun task=createTask(ownerId,context.project(),TaskType.DETAILED_ANALYSIS,"IDEA_VERSION",context.idea().getId().toString(),conceptsInput("shortlistedConcepts",selected));
        run.start(task); detailedRuns.save(run);
        try { JsonNode result=execute(task,value->validateDetailed(value,ids)); persistence.completeDetailed(run.getId(),result,request.financials()); }
        catch(ExecutionFailure failure){ persistence.failDetailed(run.getId(),failure.reason()); throw publicFailure(failure); }
        catch(RuntimeException failure){ persistence.failDetailed(run.getId(),"AI_RESULT_INVALID"); throw normalized(failure); }
        return currentDetailed(ownerId,projectId);
    }

    public DetailedView currentDetailed(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        DetailedAnalysisRun run=detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(run==null) return null;
        ShortlistDecision shortlist=shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(run.getState()==ConceptAiRunBase.State.SUCCEEDED&&shortlist!=null){
            Set<Long> expected=new HashSet<>(idList(shortlist.getSelectedConceptVersionIdsJson()));
            Set<Long> actual=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(run.getId()).stream().map(v->v.getConceptVersion().getId()).collect(java.util.stream.Collectors.toSet());
            if(!actual.equals(expected)) return null;
        }
        return detailedView(run);
    }

    public SelectionView select(Long ownerId,Long projectId,SelectionRequest request) {
        Context context=context(ownerId,projectId,false); String reason=trim(request.reason(),2000);
        if(reason==null||request.conceptVersionId()==null) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ConceptVersion concept=concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(
            List.of(request.conceptVersionId()),projectId,context.idea().getId()).stream().findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));
        if(detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(currentDetailedRunId(projectId,context.idea().getId())).stream().noneMatch(v->v.getConceptVersion().getId().equals(concept.getId()))) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ConceptSelection existing=selections.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        return selectionView(existing!=null?existing:selections.save(ConceptSelection.create(context.project(),context.idea(),concept,reason)));
    }

    public SelectionView currentSelection(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return selections.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::selectionView).orElse(null);
    }

    private Context context(Long ownerId,Long projectId,boolean requireLegalPass) {
        Project project=projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
        IdeaVersion idea=ideaVersions.findCurrent(projectId).filter(IdeaVersion::isConfirmed).orElseThrow(()->new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        IdeaOriginVersion origin=ideaOrigins.findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(projectId,IdeaOriginVersion.State.CONFIRMED).orElse(null);
        LegalPrecheckVersion legal=legalPrechecks.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId).orElse(null);
        LegalGuardrailSet guardrail=legal==null?null:guardrailSets.findByLegalPrecheckVersionIdAndDeletedAtIsNull(legal.getId()).orElse(null);
        if(requireLegalPass&&(origin==null||legal==null||guardrail==null||!legal.getIdeaOriginVersion().getId().equals(origin.getId())||!legal.isConceptBuilderAllowed()))throw new BusinessException(ErrorCode.PROJECT_STAGE_INVALID);
        return new Context(project,idea,origin,legal,guardrail);
    }
    private List<ConceptVersion> requireConcepts(Context context){ List<ConceptVersion> values=concepts.findCurrentForIdea(context.project().getId(),context.idea().getId()); if(values.isEmpty()) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID); return values; }
    private TaskRun createTask(Long ownerId,Project project,TaskType type,String subjectType,String subjectId,String input){ String nonce=UUID.randomUUID().toString(); return taskRuns.create(ownerId,project.getId(),type,subjectType,subjectId,input,hasher.hash(type,"1.0","ko-KR",input),nonce,nonce,1); }
    private JsonNode execute(TaskRun run,java.util.function.Consumer<JsonNode> validator){ TaskRunService.Claim claim=taskRuns.claim(run.getId(),"journey-sync",Duration.ofMinutes(2),Duration.ofMinutes(2)); taskRuns.startExecution(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken()); try{ var response=ai.execute(taskRuns.getOwnedForWorker(run.getId()),claim.taskAttemptId(),LocalDateTime.now().plusMinutes(2)); try{validator.accept(response.result());}catch(BusinessException invalid){taskRuns.rejectAndFail(run.getId(),claim.taskAttemptId(),claim.claimToken(),response.result().toString(),response.resultSchemaVersion(),"AI_RESULT_INVALID");throw invalid;} taskRuns.adopt(run.getId(),claim.taskAttemptId(),claim.claimToken(),response.result().toString(),response.canonicalInputHash(),response.resultSchemaVersion()); return response.result(); }catch(ExecutionFailure failure){taskRuns.fail(run.getId(),claim.taskAttemptId(),claim.claimToken(),failure.code(),failure.reason(),failure.retryable());throw failure;} }
    private String conceptGenerationInput(Context c,int desired,int round,List<String> negatives,List<ConceptDraft> accepted){ObjectNode value=mapper.createObjectNode();value.put("desiredCount",desired);value.put("round",round);value.set("ideaOrigin",parse(c.origin().getSnapshotJson()));value.set("lockedValues",parse(c.origin().getConfirmedValuesJson()));value.set("requiredOriginTrace",requiredOriginTrace(c.origin()));value.set("legalGuardrail",guardrailJson(c.guardrail()));value.set("negativeConstraints",mapper.valueToTree(new LinkedHashSet<>(negatives)));ArrayNode prior=value.putArray("acceptedConcepts");accepted.forEach(d->prior.add(parse(d.getDraftJson())));return taskInput("concept-eligibility-generation",value.toString());}
    private ArrayNode requiredOriginTrace(IdeaOriginVersion origin){JsonNode snapshot=parse(origin.getSnapshotJson());JsonNode locked=parse(origin.getConfirmedValuesJson());Map<String,JsonNode> required=new LinkedHashMap<>();for(String key:List.of("problem","target","coreValue","fixedValues"))required.put(key,snapshot.get(key));if(locked!=null&&locked.isObject())for(String key:locked.propertyNames())required.put(key,locked.path(key).path("value"));ArrayNode values=mapper.createArrayNode();required.forEach((key,source)->{ObjectNode item=values.addObject();item.put("structureKey",key);item.set("sourceValue",source==null?mapper.nullNode():source);});return values;}
    private String conceptLegalBatchInput(Context c,List<LegalCandidate> candidates){ObjectNode value=mapper.createObjectNode();value.set("guardrails",guardrailJson(c.guardrail()));value.set("lockedValues",parse(c.origin().getConfirmedValuesJson()));ArrayNode drafts=value.putArray("conceptDrafts");for(LegalCandidate candidate:candidates){ObjectNode item=candidate.candidate().deepCopy();item.put("candidateKey",candidate.candidateKey());drafts.add(item);}ObjectNode input=(ObjectNode)parse(taskInput("concept-legal-validation-batch",value.toString()));input.put("validationMode","GUARDRAIL_BATCH");input.put("guardrailVersionId",c.guardrail().getId());return input.toString();}
    private ObjectNode guardrailJson(LegalGuardrailSet g){ObjectNode value=mapper.createObjectNode();value.set("hardConstraints",parse(g.getHardConstraintsJson()));value.set("prohibitedPatterns",parse(g.getProhibitedPatternsJson()));value.set("conditionalConstraints",parse(g.getConditionalConstraintsJson()));value.set("requiredDisclosures",parse(g.getRequiredDisclosuresJson()));value.set("requiredOperationalControls",parse(g.getRequiredOperationalControlsJson()));return value;}
    private String eligibilityInputHash(Context c){if(c.origin()==null||c.guardrail()==null)return "";return sha256(c.origin().getId()+":"+c.origin().getSnapshotJson()+":"+c.origin().getConfirmedValuesJson()+":"+c.guardrail().getId()+":"+guardrailJson(c.guardrail()));}
    private String conceptsInput(String key,List<ConceptVersion> values){ var root=mapper.createObjectNode(); var array=root.putArray(key); values.forEach(v->{ObjectNode item=array.addObject(); item.put("conceptVersionId",v.getId()); item.put("name",v.getName()); item.put("summary",v.getOneLineSummary()); item.put("targetCustomer",v.getTargetCustomer()); item.put("valueProposition",v.getValueProposition()); item.put("revenueModel",v.getRevenueModel()); item.set("risks",parse(v.getRisksJson()));}); return taskInput(key,root.toString()); }
    private String taskInput(String key,String text){
        ObjectNode content=mapper.createObjectNode(); content.put("contentKey",key); content.put("contentType","TEXT");
        content.put("language","ko-KR"); content.put("totalCharacters",text.codePointCount(0,text.length())); content.put("contentHash",sha256(text));
        var chunks=content.putArray("chunks"); int offset=0,index=0;
        while(offset<text.length()){
            int count=Math.min(16_000,text.codePointCount(offset,text.length())); int next=text.offsetByCodePoints(offset,count);
            String value=text.substring(offset,next); ObjectNode chunk=chunks.addObject(); chunk.put("index",index++); chunk.put("text",value);
            chunk.put("characterCount",count); chunk.put("chunkHash",sha256(value)); offset=next;
        }
        ObjectNode root=mapper.createObjectNode(); root.putArray("textContents").add(content); return root.toString();
    }
    private String sha256(String text){try{return "sha256:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private void validateConceptGeneration(JsonNode r,int expected){if(r==null||!r.has("concepts")||!r.get("concepts").isArray()||r.get("concepts").size()!=expected)throw invalid();for(JsonNode c:r.get("concepts")){text(c,"conceptName");text(c,"positioning");if(c.get("conceptName").asText().length()>200)throw invalid();for(String f:List.of("targetSegment","pricing","revenueModel","operatingModel"))object(c,f);for(String f:List.of("featureSet","channels","newAssumptions","newBusinessActivities","originTrace","legalTrace"))array(c,f);if(c.get("originTrace").isEmpty())throw invalid();}}
    private void validateConceptLegalBatch(JsonNode r,Set<String> expectedKeys){if(r==null||!r.isObject()||!Set.copyOf(r.propertyNames()).equals(Set.of("validations"))||!r.has("validations")||!r.get("validations").isArray())throw invalid();Set<String> actualKeys=new HashSet<>();for(JsonNode validation:r.get("validations")){if(!validation.isObject()||!Set.copyOf(validation.propertyNames()).equals(Set.of("candidateKey","status","reasons","violatedStructureKeys","legalTrace")))throw invalid();String key=text(validation,"candidateKey");if(!actualKeys.add(key)||!expectedKeys.contains(key)||!Set.of("PASS","FAIL_LEGAL").contains(text(validation,"status")))throw invalid();for(String field:List.of("reasons","violatedStructureKeys"))stringArray(validation,field);array(validation,"legalTrace");for(JsonNode trace:validation.get("legalTrace")){if(!trace.isObject()||!Set.copyOf(trace.propertyNames()).equals(Set.of("guardrailType","constraint","implementation")))throw invalid();text(trace,"guardrailType");text(trace,"constraint");text(trace,"implementation");}}if(!actualKeys.equals(expectedKeys)||r.get("validations").size()!=expectedKeys.size())throw invalid();}
    private Validation originIntegrity(JsonNode candidate,IdeaOriginVersion origin){List<String> reasons=new ArrayList<>();List<String> keys=new ArrayList<>();JsonNode snapshot=parse(origin.getSnapshotJson());JsonNode locked=parse(origin.getConfirmedValuesJson());Map<String,JsonNode> expected=new LinkedHashMap<>();for(String key:List.of("problem","target","coreValue","fixedValues"))expected.put(key,snapshot.get(key));if(locked.isObject())for(String key:locked.propertyNames())expected.put(key,locked.path(key).path("value"));Map<String,JsonNode> traces=new HashMap<>();for(JsonNode trace:candidate.path("originTrace")){if(!trace.isObject()||!trace.has("structureKey")||!trace.has("sourceValue")||!trace.has("conceptValue")){reasons.add("ORIGIN_TRACE_INVALID");continue;}traces.put(trace.path("structureKey").asText(),trace);}for(var entry:expected.entrySet()){JsonNode trace=traces.get(entry.getKey());if(trace==null||entry.getValue()==null||!entry.getValue().equals(trace.get("sourceValue"))){reasons.add("ORIGIN_VALUE_NOT_PRESERVED:"+entry.getKey());keys.add(entry.getKey());continue;}JsonNode actual=switch(entry.getKey()){case "target"->candidate.get("targetSegment");case "pricingIntent"->candidate.get("pricing");case "revenueModelIntent"->candidate.get("revenueModel");case "salesChannelIntent"->candidate.get("channels");default->trace.get("conceptValue");};if(actual==null||!actual.equals(trace.get("conceptValue"))){reasons.add("ORIGIN_TRACE_CONCEPT_MISMATCH:"+entry.getKey());keys.add(entry.getKey());}}return new Validation(reasons.isEmpty(),List.copyOf(reasons),List.copyOf(keys));}
    private String fingerprint(JsonNode candidate){return sha256(normalize(candidate.path("targetSegment").toString()+candidate.path("positioning").asText()+candidate.path("featureSet").toString()+candidate.path("revenueModel").toString()+candidate.path("channels").toString()+candidate.path("operatingModel").toString()));}
    private String normalize(String value){return value.toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    private List<String> strings(JsonNode node){List<String> values=new ArrayList<>();if(node!=null&&node.isArray())for(JsonNode value:node)if(value.isTextual()&&!value.asText().isBlank())values.add(value.asText());return values;}
    private void stringArray(JsonNode n,String f){array(n,f);for(JsonNode value:n.get(f))if(!value.isTextual()||value.asText().isBlank())throw invalid();}
    private void object(JsonNode n,String f){if(n.get(f)==null||!n.get(f).isObject())throw invalid();}
    private void validateQuick(JsonNode r,List<ConceptVersion> expected){validateIds(r,"assessments",expected.stream().map(ConceptVersion::getId).toList());for(JsonNode a:r.get("assessments")){for(String f:List.of("market","customerValue","feasibility","differentiation","revenuePotential","legalRisk")){int s=a.get(f).asInt(-1);if(s<0||s>100)throw invalid();}if(!a.has("overallScore")||!a.get("overallScore").isNumber()||a.get("overallScore").asDouble()<0||a.get("overallScore").asDouble()>100)throw invalid();text(a,"summary");array(a,"strengths");array(a,"weaknesses");}}
    private void validateDetailed(JsonNode r,List<Long> ids){validateIds(r,"analyses",ids);for(JsonNode a:r.get("analyses")){for(String f:List.of("marketAnalysis","customerAnalysis","businessModelAnalysis","operationAnalysis","riskAnalysis","recommendation"))text(a,f);array(a,"assumptions");array(a,"researchNeeds");}}
    private void validateIds(JsonNode r,String field,List<Long> expected){if(r==null||!r.has(field)||!r.get(field).isArray())throw invalid();Set<Long> actual=new HashSet<>();r.get(field).forEach(v->{if(!v.has("conceptVersionId")||!v.get("conceptVersionId").canConvertToLong())throw invalid();actual.add(v.get("conceptVersionId").asLong());});if(!actual.equals(new HashSet<>(expected))||actual.size()!=r.get(field).size())throw invalid();}
    private void validateFinancialInputs(List<FinancialInput> values,List<Long> ids){if(values==null||values.size()!=ids.size()||!values.stream().map(FinancialInput::conceptVersionId).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(ids)))throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);for(FinancialInput v:values){if(v.unitPrice()==null||v.variableCostPerCustomer()==null||v.monthlyFixedCost()==null||v.initialInvestment()==null||v.unitPrice().signum()<=0||v.monthlyCustomers()<0||v.variableCostPerCustomer().signum()<0||v.monthlyFixedCost().signum()<0||v.initialInvestment().signum()<0||v.unitPrice().compareTo(v.variableCostPerCustomer())<=0)throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);}}
    private String text(JsonNode n,String f){if(n==null||!n.has(f)||!n.get(f).isTextual()||n.get(f).asText().isBlank())throw invalid();return n.get(f).asText();} private void array(JsonNode n,String f){if(!n.has(f)||!n.get(f).isArray())throw invalid();}
    private BusinessException invalid(){return new BusinessException(ErrorCode.AI_RESULT_INVALID);} private BusinessException normalized(RuntimeException f){return f instanceof BusinessException b?b:new BusinessException(ErrorCode.AI_RESULT_INVALID);} private BusinessException publicFailure(ExecutionFailure f){if("AI_CONFIGURATION_INVALID".equals(f.reason()))return new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);if("AI_RESULT_INVALID".equals(f.reason())||"RESULT_SCHEMA_INVALID".equals(f.code()))return invalid();return new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE);}
    private JsonNode parse(String value){return value==null?null:mapper.readTree(value);} private String trim(String value,int max){if(value==null||value.isBlank())return null;String t=value.trim();return t.substring(0,Math.min(max,t.length()));} private List<Long> distinctIds(List<Long> ids){return ids==null?List.of():ids.stream().filter(Objects::nonNull).distinct().toList();} private List<Long> idList(String json){JsonNode n=parse(json);List<Long> ids=new ArrayList<>();n.forEach(v->ids.add(v.asLong()));return ids;} private Long currentDetailedRunId(Long p,Long i){return detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(p,i).filter(r->r.getState()==ConceptAiRunBase.State.SUCCEEDED).map(DetailedAnalysisRun::getId).orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));}

    private ConceptView conceptView(ConceptVersion v){return new ConceptView(v.getId(),v.getConcept().getId(),v.getConcept().getDisplayOrder(),v.getName(),v.getOneLineSummary(),v.getTargetCustomer(),v.getProblem(),v.getSolution(),v.getValueProposition(),v.getRevenueModel(),parse(v.getKeyFeaturesJson()),parse(v.getDifferentiatorsJson()),parse(v.getAssumptionsJson()),parse(v.getRisksJson()),v.getIdeaVersion().getId(),v.getEligibilityStatus(),parse(v.getTargetSegmentJson()),v.getPositioning(),parse(v.getPricingJson()),parse(v.getChannelsJson()),parse(v.getOperatingModelJson()),parse(v.getNewBusinessActivitiesJson()),parse(v.getOriginTraceJson()),parse(v.getLegalTraceJson()));}
    private String conceptFailureCode(ExecutionFailure failure){if("AI_CONFIGURATION_INVALID".equals(failure.reason()))return "AI_CONFIGURATION_INVALID";return switch(failure.code()){case "DEADLINE_EXCEEDED"->"TASK_TIMEOUT";case "INVALID_REQUEST","UNSUPPORTED_CONTRACT_VERSION","UNSUPPORTED_TASK_TYPE","UNSUPPORTED_TASK_SCHEMA_VERSION","RESULT_SCHEMA_INVALID"->"AI_RESULT_INVALID";default->"AI_SERVICE_UNAVAILABLE";};}
    private BatchView batchView(ConceptEligibilityBatch b,boolean stale){List<ConceptView> eligible=!stale&&b.getState()==ConceptEligibilityBatch.State.COMPLETED?concepts.findByEligibilityBatchIdAndEligibilityStatusAndDeletedAtIsNullOrderByConceptDisplayOrder(b.getId(),"ELIGIBLE").stream().map(this::conceptView).toList():List.of();return new BatchView(b.getId(),b.getState().name(),b.getCurrentRound(),b.getInspectedCandidates(),b.getEligibleCandidates(),b.getTargetEligibleCount(),b.getMaxReplacementRounds(),b.getMaxInspectedCandidates(),parse(b.getNeedsInputJson()),b.getErrorCode(),b.isRetryable(),stale,b.getIdeaOriginVersion().getId(),b.getLegalGuardrailSet().getId(),b.getInputSnapshotHash(),eligible);}
    private QuickView quickView(QuickAssessmentRun run){List<QuickAssessmentView> values=quickAssessments.findByRunIdAndDeletedAtIsNullOrderByOverallScoreDesc(run.getId()).stream().map(a->new QuickAssessmentView(a.getConceptVersion().getId(),a.getConceptVersion().getName(),a.getMarketScore(),a.getCustomerValueScore(),a.getFeasibilityScore(),a.getDifferentiationScore(),a.getRevenuePotentialScore(),a.getLegalRiskScore(),a.getOverallScore(),a.getSummary(),parse(a.getStrengthsJson()),parse(a.getWeaknessesJson()))).toList();return new QuickView(run.getId(),run.getState().name(),run.getIdeaVersion().getId(),values,run.getError(),run.getCompletedAt());}
    private ShortlistView shortlistView(ShortlistDecision s){return new ShortlistView(s.getId(),s.getIdeaVersion().getId(),idList(s.getSelectedConceptVersionIdsJson()),s.getReason(),s.getCreatedAt());}
    private DetailedView detailedView(DetailedAnalysisRun run){List<DetailedItemView> items=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(run.getId()).stream().map(a->new DetailedItemView(a.getConceptVersion().getId(),a.getConceptVersion().getName(),a.getMarketAnalysis(),a.getCustomerAnalysis(),a.getBusinessModelAnalysis(),a.getOperationAnalysis(),a.getRiskAnalysis(),a.getRecommendation(),parse(a.getAssumptionsJson()),parse(a.getResearchNeedsJson()))).toList();Map<Long,FinancialView> finance=new HashMap<>();financials.findByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderById(run.getProject().getId(),run.getIdeaVersion().getId()).forEach(f->finance.put(f.getConceptVersion().getId(),financialView(f)));return new DetailedView(run.getId(),run.getState().name(),run.getIdeaVersion().getId(),items,finance,run.getError(),run.getCompletedAt());}
    private FinancialView financialView(JourneyFinancialAnalysis f){return new FinancialView(f.getConceptVersion().getId(),f.getUnitPrice(),f.getMonthlyCustomers(),f.getVariableCostPerCustomer(),f.getMonthlyFixedCost(),f.getInitialInvestment(),f.getMonthlyRevenue(),f.getMonthlyVariableCost(),f.getMonthlyTotalCost(),f.getMonthlyOperatingProfit(),f.getBreakEvenCustomers(),f.getPaybackMonths());}
    private SelectionView selectionView(ConceptSelection s){return new SelectionView(s.getId(),s.getIdeaVersion().getId(),s.getConceptVersion().getId(),s.getConceptVersion().getName(),s.getReason(),s.getCreatedAt());}

    private record Context(Project project,IdeaVersion idea,IdeaOriginVersion origin,LegalPrecheckVersion legal,LegalGuardrailSet guardrail){}
    private record Validation(boolean pass,List<String> reasons,List<String> keys){}
    private record LegalCandidate(String candidateKey,ConceptDraft draft,ObjectNode candidate){}
    public record ShortlistRequest(List<Long> conceptVersionIds,String reason){}
    public record FinancialInput(Long conceptVersionId,BigDecimal unitPrice,int monthlyCustomers,BigDecimal variableCostPerCustomer,BigDecimal monthlyFixedCost,BigDecimal initialInvestment){}
    public record DetailedRequest(List<FinancialInput> financials){}
    public record SelectionRequest(Long conceptVersionId,String reason){}
    public record ConceptView(Long id,Long conceptId,int displayOrder,String name,String oneLineSummary,String targetCustomer,String problem,String solution,String valueProposition,String revenueModel,JsonNode keyFeatures,JsonNode differentiators,JsonNode assumptions,JsonNode risks,Long ideaVersionId,String eligibilityStatus,JsonNode targetSegment,String positioning,JsonNode pricing,JsonNode channels,JsonNode operatingModel,JsonNode newBusinessActivities,JsonNode originTrace,JsonNode legalTrace){}
    public record BatchView(Long id,String state,int currentRound,int inspectedCandidates,int eligibleCandidates,int targetEligibleCount,int maxReplacementRounds,int maxInspectedCandidates,JsonNode needsInput,String errorCode,boolean retryable,boolean stale,Long ideaOriginVersionId,Long legalGuardrailSetId,String inputSnapshotHash,List<ConceptView> concepts){}
    public record QuickAssessmentView(Long conceptVersionId,String conceptName,int market,int customerValue,int feasibility,int differentiation,int revenuePotential,int legalRisk,BigDecimal overallScore,String summary,JsonNode strengths,JsonNode weaknesses){}
    public record QuickView(Long id,String state,Long ideaVersionId,List<QuickAssessmentView> assessments,String error,LocalDateTime completedAt){}
    public record ShortlistView(Long id,Long ideaVersionId,List<Long> conceptVersionIds,String reason,LocalDateTime createdAt){}
    public record DetailedItemView(Long conceptVersionId,String conceptName,String marketAnalysis,String customerAnalysis,String businessModelAnalysis,String operationAnalysis,String riskAnalysis,String recommendation,JsonNode assumptions,JsonNode researchNeeds){}
    public record FinancialView(Long conceptVersionId,BigDecimal unitPrice,int monthlyCustomers,BigDecimal variableCostPerCustomer,BigDecimal monthlyFixedCost,BigDecimal initialInvestment,BigDecimal monthlyRevenue,BigDecimal monthlyVariableCost,BigDecimal monthlyTotalCost,BigDecimal monthlyOperatingProfit,int breakEvenCustomers,BigDecimal paybackMonths){}
    public record DetailedView(Long id,String state,Long ideaVersionId,List<DetailedItemView> analyses,Map<Long,FinancialView> financials,String error,LocalDateTime completedAt){}
    public record SelectionView(Long id,Long ideaVersionId,Long conceptVersionId,String conceptName,String reason,LocalDateTime createdAt){}
}
