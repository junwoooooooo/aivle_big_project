package com.aivle.backend.pipeline.marketing.visual.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.marketing.application.MarketingLegalGuard;
import com.aivle.backend.pipeline.marketing.domain.MarketingAsset;
import com.aivle.backend.pipeline.marketing.repository.MarketingAssetRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class MarketingVisualCompletionService {
    private final ProjectEvidenceArtifactService artifacts;
    private final MarketingAssetRepository marketingAssets;
    private final MarketingLegalGuard legalGuard;
    private final TaskRunService taskRuns;
    private final ObjectMapper mapper;

    @Transactional
    public void complete(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        ObjectNode result = validate(response.result());
        JsonNode input = mapper.readTree(context.inputSnapshot());
        legalGuard.validateVisual(mapper.writeValueAsString(input.path("source")), result);
        byte[] image = decode(result.path("banner").path("imageBase64").asText());
        var artifact = artifacts.storeGenerated(context.ownerId(), context.projectId(),
            "marketing-visual-" + context.taskRunId() + ".jpg", "image/jpeg", image);
        ((ObjectNode) result.path("banner")).remove("imageBase64");
        ObjectNode artifactNode = mapper.createObjectNode();
        artifactNode.put("artifactId", artifact.artifactId());
        artifactNode.put("filename", artifact.originalFilename());
        artifactNode.put("mediaType", artifact.mediaType());
        artifactNode.put("sizeBytes", artifact.sizeBytes());
        artifactNode.put("downloadPath", "/api/v3/projects/" + context.projectId()
            + "/evidence-artifacts/" + artifact.artifactId() + "/download");
        result.set("artifact", artifactNode);
        result.put("sourceTaskRunId", context.taskRunId());
        result.put("marketingContentId", input.path("marketingContentId").asText());
        result.put("marketingRevisionId", input.path("marketingRevisionId").asText());
        result.put("callToAction", input.path("content").path("callToAction").asText());
        result.put("sourceImageArtifactId", input.path("sourceImage").path("artifactId").asText());
        result.set("visual", input.path("visual").deepCopy());
        taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
            mapper.writeValueAsString(result), response.canonicalInputHash(), response.resultSchemaVersion());
        marketingAssets.save(MarketingAsset.link(input.path("marketingContentId").asText(),
            input.path("marketingRevisionId").asText(), artifact.artifactId()));
    }

    @Transactional
    public void fail(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }

    private ObjectNode validate(JsonNode value) {
        if (!(value instanceof ObjectNode result)
                || !"marketing-visual-generation-result-v1".equals(result.path("contract").asText())
                || result.path("generatedCopy").path("badge").asText().isBlank()
                || result.path("generatedCopy").path("headline").asText().isBlank()
                || result.path("generatedCopy").path("subheadline").asText().isBlank()
                || !"image/jpeg".equals(result.path("banner").path("mediaType").asText())
                || result.path("banner").path("imageBase64").asText().isBlank()
                || !result.path("legalReview").path("compliant").asBoolean(false)) {
            throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        return result.deepCopy();
    }

    private byte[] decode(String encoded) {
        try {
            byte[] image = Base64.getDecoder().decode(encoded);
            if (image.length == 0 || image.length > MarketingVisualService.MAX_IMAGE_BYTES
                    || image.length < 3 || (image[0] & 0xff) != 0xff
                    || (image[1] & 0xff) != 0xd8 || (image[2] & 0xff) != 0xff) {
                throw new IllegalArgumentException("invalid generated image");
            }
            return image;
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
        }
    }
}
