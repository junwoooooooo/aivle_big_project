package com.aivle.backend.integration.ai;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureAiResponse;
public interface AiServiceClient {
    AiJobAcceptedResponse startJob(AiJobRequest request);
    AiJobStatusResponse getStatus(String externalRequestId);
    void cancel(String externalRequestId);
    DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request);
}
