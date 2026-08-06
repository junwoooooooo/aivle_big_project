package com.aivle.backend.pipeline.marketing.application;

import static com.aivle.backend.pipeline.marketing.api.MarketingApiModels.*;

import com.aivle.backend.common.exception.*;
import com.aivle.backend.jobevent.*;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.pipeline.planning.domain.FinalizedPlanningSnapshot;
import com.aivle.backend.pipeline.planning.repository.FinalizedPlanningSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.service.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

@Service @RequiredArgsConstructor
public class MarketingContentService {
    public static final String REQUEST_CONTRACT = "marketing-content-request-v1";
    private final ProjectRepository projects;
    private final FinalizedPlanningSnapshotRepository planning;
    private final MarketingContentRepository contents;
    private final MarketingContentRevisionRepository revisions;
    private final MarketingAssetRepository assets;
    private final MarketingSourceSnapshotFactory sources;
    private final MarketingResultContract results;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    @Transactional
    public ContentView create(Long ownerId, Long projectId, CreateRequest request, String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId); validateRequest(request);
        FinalizedPlanningSnapshot finalized = currentPlanning(projectId);
        if (!finalized.getId().equals(request.planningSnapshotId())) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        ObjectNode source = source(finalized); String sourceJson = mapper.writeValueAsString(source);
        String requestJson = mapper.writeValueAsString(request); String id = UUID.randomUUID().toString();
        String title = source.path("conceptName").asText("Marketing") + " - " + request.contentType().name();
        MarketingContent content = contents.save(MarketingContent.queued(id, projectId, finalized.getId(),
            source.path("sourceSnapshotHash").asText(), sourceJson, requestJson, request.contentType(), request.channel(), title, ownerId));
        String taskId = enqueue(ownerId, projectId, content, requestJson, sourceJson, idempotencyKey, correlationId);
        content.attachTaskRun(taskId); publish(projectId, taskId, "QUEUED", "job.marketing.queued", JobEvent.Status.QUEUED, null);
        return view(content, finalized);
    }

    @Transactional(readOnly = true)
    public ContentListView list(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId); FinalizedPlanningSnapshot current = planning.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId).orElse(null);
        return new ContentListView(contents.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).stream().map(c -> summary(c,current)).toList());
    }
    @Transactional(readOnly = true)
    public ContentView get(Long ownerId, Long projectId, String id) { requireOwned(ownerId, projectId); return view(find(projectId,id), planning.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId).orElse(null)); }

    @Transactional
    public ContentView edit(Long ownerId, Long projectId, String id, EditRequest request) {
        requireOwned(ownerId, projectId); MarketingContent content=findLocked(projectId,id);
        if (!Set.of(MarketingRevisionType.TONE_EDITED, MarketingRevisionType.SHORTENED, MarketingRevisionType.LEGAL_NOTICE_APPLIED, MarketingRevisionType.USER_EDITED).contains(request.revisionType()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        try { results.validate(request.result(), content.getContentType()); validateClaims(content,request.result()); int number=content.addUserRevision();
            revisions.save(MarketingContentRevision.create(id,number,request.revisionType(),MarketingRevisionOrigin.USER,mapper.writeValueAsString(request.result()),ownerId));
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage()); }
        return view(content,currentPlanning(projectId));
    }

    @Transactional
    public ContentView regenerate(Long ownerId, Long projectId, String id, String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId); MarketingContent content=findLocked(projectId,id); FinalizedPlanningSnapshot finalized=currentPlanning(projectId);
        ObjectNode source=source(finalized); ObjectNode request=(ObjectNode)mapper.readTree(content.getRequestJson()); request.put("planningSnapshotId",finalized.getId());
        String requestJson=mapper.writeValueAsString(request); String sourceJson=mapper.writeValueAsString(source);
        String taskId=enqueue(ownerId,projectId,content,requestJson,sourceJson,idempotencyKey,correlationId);
        try { content.regenerate(finalized.getId(),source.path("sourceSnapshotHash").asText(),sourceJson,requestJson,taskId); }
        catch (IllegalStateException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST,invalid.getMessage()); }
        publish(projectId,taskId,"QUEUED","job.marketing.queued",JobEvent.Status.QUEUED,null); return view(content,finalized);
    }

    @Transactional
    public ContentView finalizeContent(Long ownerId,Long projectId,String id) {
        requireOwned(ownerId,projectId); MarketingContent content=findLocked(projectId,id); FinalizedPlanningSnapshot current=currentPlanning(projectId);
        if (stale(content,current)) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        MarketingContentRevision latest=revisions.findFirstByContentIdAndDeletedAtIsNullOrderByRevisionNumberDesc(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try { validateClaims(content,mapper.readTree(latest.getResultJson())); int number=content.finalizeContent(Instant.now()); revisions.save(MarketingContentRevision.create(id,number,MarketingRevisionType.FINALIZED,MarketingRevisionOrigin.SYSTEM,latest.getResultJson(),ownerId)); }
        catch (IllegalStateException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST,invalid.getMessage()); }
        return view(content,current);
    }

    private String enqueue(Long ownerId,Long projectId,MarketingContent content,String requestJson,String sourceJson,String key,String correlation) {
        if (key==null||key.isBlank()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        ObjectNode input=mapper.createObjectNode(); input.set("source",mapper.readTree(sourceJson)); input.set("request",mapper.readTree(requestJson));
        String json=mapper.writeValueAsString(input); String hash=inputHasher.hash(TaskType.MARKETING_CONTENT_GENERATION,"1.0","ko-KR",json);
        return taskRuns.create(ownerId,projectId,TaskType.MARKETING_CONTENT_GENERATION,"MARKETING_CONTENT",content.getId(),json,hash,key,correlation==null||correlation.isBlank()?UUID.randomUUID().toString():correlation,2).getId();
    }
    private ContentView view(MarketingContent c,FinalizedPlanningSnapshot current){return new ContentView(summary(c,current),mapper.readTree(c.getSourceSnapshotJson()),mapper.readTree(c.getRequestJson()),revisions.findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(c.getId()).stream().map(r->new RevisionView(r.getId(),r.getRevisionNumber(),r.getRevisionType(),r.getOrigin(),mapper.readTree(r.getResultJson()))).toList(),assets.findAllByContentIdAndDeletedAtIsNull(c.getId()).stream().map(MarketingAsset::getArtifactRef).toList());}
    private ContentSummary summary(MarketingContent c,FinalizedPlanningSnapshot current){String status=stale(c,current)?"STALE":c.getStatus().name();String activeJobId=("QUEUED".equals(status)||"RUNNING".equals(status))?c.getTaskRunId():null;return new ContentSummary(c.getId(),c.getPlanningSnapshotId(),c.getSourceSnapshotHash(),c.getContentType(),c.getChannel(),c.getTitle(),status,c.getCurrentRevisionNumber(),c.getTaskRunId(),activeJobId,c.getPlanningSnapshotId(),c.getUpdatedAt(),c.getFinalizedAt());}
    private boolean stale(MarketingContent c,FinalizedPlanningSnapshot current){return current!=null&&(!current.getId().equals(c.getPlanningSnapshotId())||!sourceMatches(current,c.getSourceSnapshotHash()));}
    private boolean sourceMatches(FinalizedPlanningSnapshot snapshot,String hash){try{return sources.matches(snapshot,hash);}catch(IllegalArgumentException invalid){throw new BusinessException(ErrorCode.PLAN_INCOMPLETE);}}
    private void validateClaims(MarketingContent content,JsonNode result){JsonNode source=mapper.readTree(content.getSourceSnapshotJson());String rendered=(result.path("title").asText()+"\n"+result.path("body").asText()+"\n"+result.path("callToAction").asText()+"\n"+result.path("imageBrief").asText()).toLowerCase(Locale.ROOT);for(JsonNode claim:source.path("prohibitedClaims"))if(!claim.asText().isBlank()&&rendered.contains(claim.asText().toLowerCase(Locale.ROOT)))throw new BusinessException(ErrorCode.MARKETING_PROHIBITED_CLAIM);}
    private ObjectNode source(FinalizedPlanningSnapshot snapshot){try{return sources.create(snapshot);}catch(IllegalArgumentException invalid){throw new BusinessException(ErrorCode.PLAN_INCOMPLETE);}}
    private void validateRequest(CreateRequest r){if(!REQUEST_CONTRACT.equals(r.contract()))throw new BusinessException(ErrorCode.INVALID_REQUEST);}
    private FinalizedPlanningSnapshot currentPlanning(Long projectId){return planning.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId).orElseThrow(()->new BusinessException(ErrorCode.PLAN_INCOMPLETE));}
    private MarketingContent find(Long projectId,String id){return contents.findByIdAndProjectIdAndDeletedAtIsNull(id,projectId).orElseThrow(()->new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));}
    private MarketingContent findLocked(Long projectId,String id){return contents.findLocked(id,projectId).orElseThrow(()->new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));}
    private void requireOwned(Long ownerId,Long projectId){projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private void publish(Long projectId,String taskId,String stage,String type,JobEvent.Status status,String code){events.publish(new JobEventPublisher.Command(projectId,taskId,taskId,stage,type,status,type,Map.of(),code));}
}
