package com.aivle.backend.journey.conversation;

import com.aivle.backend.journey.brief.OpportunityBriefVersionRepository;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaTurnCompletionService {
    private final OpportunityBriefWorkspaceService briefs;
    private final OpportunityBriefVersionRepository versions;
    private final ConversationService conversations;
    private final IdeaMessageRepository messages;
    private final TaskRunService tasks;

    public IdeaTurnCompletionService(OpportunityBriefWorkspaceService briefs,
            OpportunityBriefVersionRepository versions, ConversationService conversations,
            IdeaMessageRepository messages, TaskRunService tasks) {
        this.briefs = briefs; this.versions = versions; this.conversations = conversations;
        this.messages = messages; this.tasks = tasks;
    }

    @Transactional
    public void complete(Long ownerId, Long projectId, Long conversationId, Long sourceMessageId,
            String taskRunId, String inputHash, TaskRunService.Claim claim, String resultJson,
            List<OpportunityBriefWorkspaceService.AiField> proposals,
            IdeaMessageContract.Envelope envelope) {
        TaskRun run = tasks.getOwnedForWorker(taskRunId);
        if (!run.getProject().getId().equals(projectId)
                || !run.getProject().getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("conversation task ownership mismatch");
        }
        boolean messageExists = messages.findByTaskRunIdAndDeletedAtIsNull(run.getId()).isPresent();
        boolean briefExists = versions.findByTaskRunIdAndDeletedAtIsNull(run.getId()).isPresent();
        if (messageExists != briefExists) throw new IllegalStateException("partial conversation turn result");
        if (!messageExists) {
            briefs.mergeAiDraft(ownerId, projectId, conversationId, proposals, sourceMessageId, null, run);
            conversations.appendAssistant(ownerId, projectId, conversationId, envelope, run);
        }
        tasks.adopt(taskRunId, claim.taskAttemptId(), claim.claimToken(), resultJson,
            inputHash, "1.0");
    }
}
