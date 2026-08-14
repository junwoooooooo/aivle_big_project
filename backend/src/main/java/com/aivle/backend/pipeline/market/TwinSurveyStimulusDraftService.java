package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelectionStatus;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Twin 자극 초안도 장시간 TaskRun으로 실행하며 GET은 TaskResult를 읽기만 한다. */
@Service
public class TwinSurveyStimulusDraftService {
    private static final String SCHEMA_VERSION="1.0";
    private static final Pattern PLAIN_KRW=Pattern.compile("(\\d[\\d,]*)\\s*원");
    private static final Pattern SCALED_UNIT=Pattern.compile("\\d\\s*[만억조]");
    private static final long PRICE_MAX=100_000_000L;
    private final ProjectRepository projects;
    private final ConceptPortfolioSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository snapshots;
    private final TaskRunService taskRuns;
    private final TaskRunRepository runRepository;
    private final TaskResultRepository results;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;

    public TwinSurveyStimulusDraftService(ProjectRepository projects,
            ConceptPortfolioSelectionRepository selections,
            MarketAnalysisSeedSnapshotRepository snapshots,TaskRunService taskRuns,
            TaskRunRepository runRepository,TaskResultRepository results,
            CanonicalInputHasher hasher,ObjectMapper mapper){
        this.projects=projects;this.selections=selections;this.snapshots=snapshots;this.taskRuns=taskRuns;
        this.runRepository=runRepository;this.results=results;this.hasher=hasher;this.mapper=mapper;
    }

    @Transactional
    public DraftRunView start(Long ownerId,Long projectId,String idempotencyKey,String correlationId){
        Project project=owned(ownerId,projectId);
        String input=mapper.writeValueAsString(material(projectId));
        String hash=hasher.hash(TaskType.TWIN_STIMULUS_DRAFT,SCHEMA_VERSION,"ko-KR",input);
        var created=taskRuns.createWithDisposition(ownerId,projectId,TaskType.TWIN_STIMULUS_DRAFT,
            "TWIN_STIMULUS_DRAFT",String.valueOf(projectId),input,hash,idempotencyKey,correlationId,1);
        return view(created.taskRun(),null);
    }

    @Transactional(readOnly=true)
    public DraftRunView current(Long ownerId,Long projectId){
        owned(ownerId,projectId);
        TaskRun run=runRepository
            .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId,"TWIN_STIMULUS_DRAFT",String.valueOf(projectId)).orElse(null);
        if(run==null)return null;
        JsonNode result=run.getFinalResultId()==null?null:results.findById(run.getFinalResultId())
            .map(value->mapper.readTree(value.getResultJson())).orElse(null);
        return view(run,result);
    }

    private DraftRunView view(TaskRun run,JsonNode result){
        JsonNode input=mapper.readTree(run.getInputSnapshot());
        return new DraftRunView(run.getId(),run.getState().name(),run.getLastErrorCode(),
            run.isRetryable(),result,input.path("conceptId").asText(null),input.path("conceptName").asText(null));
    }

    private ObjectNode material(Long projectId){
        var selection=selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .filter(value->value.getStatus()==ConceptPortfolioSelectionStatus.READY_FOR_MARKET
                && value.getActiveTaskRunId()==null)
            .orElseThrow(()->new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED,
                "READY_FOR_MARKET인 current selected Concept가 필요합니다."));
        var snapshot=snapshots.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId())
            .filter(value->"CONCEPT_PORTFOLIO_V2".equals(value.getSourceType()))
            .orElseThrow(()->new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE,
                "current Market Analysis Seed가 필요합니다."));
        JsonNode seed=mapper.readTree(snapshot.getSnapshotJson());
        JsonNode concept=seed.path("selectedConcept"),hypotheses=seed.path("finalHypotheses");
        ObjectNode material=mapper.createObjectNode();
        material.put("conceptId",selection.getConceptId());
        material.put("conceptName",concept.path("identity").path("conceptName").asText(""));
        material.put("targetUsers",concept.path("identity").path("targetUsers").asText(""));
        material.put("problemScenario",concept.path("solution").path("problemScenario").asText(""));
        ArrayNode features=material.putArray("featureSet");
        for(JsonNode feature:concept.path("solution").path("featureSet"))
            if(feature.isTextual()&&!feature.asText().isBlank())features.add(feature.asText());
        material.put("differentiators",hypotheses.path("differentiators").path("value").asText(""));
        Long price=priceKrw(hypotheses.path("price").path("value").asText(""));
        if(price==null)material.putNull("priceKrw");else material.put("priceKrw",price);
        if(material.path("conceptName").asText().isBlank())
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"컨셉 이름이 없습니다.");
        return material;
    }

    public static Long priceKrw(String text){
        if(text==null||SCALED_UNIT.matcher(text).find())return null;
        Matcher found=PLAIN_KRW.matcher(text);if(!found.find())return null;
        try{long value=Long.parseLong(found.group(1).replace(",",""));
            return value<0||value>PRICE_MAX?null:value;}
        catch(NumberFormatException invalid){return null;}
    }
    private Project owned(Long ownerId,Long projectId){
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId)
            .orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    public record DraftRunView(String taskRunId,String state,String errorCode,
                               boolean retryable,JsonNode result,String sourceConceptId,String sourceConceptName){}
}
