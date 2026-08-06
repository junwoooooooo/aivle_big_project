package com.aivle.backend.journey.conversation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.IdeaSource;
import com.aivle.backend.journey.IdeaSourceRepository;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class IdeaWorkspaceService {
    private final FoundationProjectAccess projectAccess;
    private final ConversationService conversations;
    private final IdeaConversationRepository conversationRepository;
    private final IdeaSourceRepository sources;
    private final IdeaAttachmentService attachments;
    private final OpportunityBriefWorkspaceService briefs;
    private final IdeaIntakeAiService ai;
    private final TaskRunRepository taskRuns;
    private final ObjectMapper mapper;

    public IdeaWorkspaceService(FoundationProjectAccess projectAccess, ConversationService conversations,
            IdeaConversationRepository conversationRepository, IdeaSourceRepository sources,
            IdeaAttachmentService attachments, OpportunityBriefWorkspaceService briefs,
            IdeaIntakeAiService ai, TaskRunRepository taskRuns, ObjectMapper mapper) {
        this.projectAccess = projectAccess; this.conversations = conversations;
        this.conversationRepository = conversationRepository; this.sources = sources;
        this.attachments = attachments; this.briefs = briefs; this.ai = ai;
        this.taskRuns = taskRuns; this.mapper = mapper;
    }

    @Transactional
    public WorkspaceView create(Long ownerId, Long projectId, boolean importCurrentIdeaSource) {
        projectAccess.requireOwned(ownerId, projectId);
        Long sourceId = importCurrentIdeaSource ? sources.findCurrent(projectId).map(IdeaSource::getId).orElse(null) : null;
        IdeaConversation conversation = conversations.create(ownerId, projectId, sourceId);
        return view(ownerId, projectId, conversation);
    }

    @Transactional(readOnly = true)
    public WorkspaceView current(Long ownerId, Long projectId) {
        IdeaConversation conversation = conversations.current(ownerId, projectId);
        return conversation == null ? null : view(ownerId, projectId, conversation);
    }

    @Transactional(readOnly = true)
    public WorkspaceView get(Long ownerId, Long projectId, Long conversationId) {
        projectAccess.requireOwned(ownerId, projectId);
        IdeaConversation conversation = conversationRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return view(ownerId, projectId, conversation);
    }

    @Transactional
    public MessageAccepted send(Long ownerId, Long projectId, Long conversationId,
            String text, List<Answer> answers) {
        if ((text == null || text.isBlank()) && (answers == null || answers.isEmpty()))
            throw new IllegalArgumentException("message text or answers are required");
        List<Answer> safeAnswers = answers == null ? List.of() : List.copyOf(answers);
        IdeaMessage message = conversations.appendMessage(ownerId, projectId, conversationId, IdeaMessage.Role.USER,
            text == null || text.isBlank() ? "답변을 제출했습니다." : text.trim());
        for (Answer answer : safeAnswers) {
            if (answer.undecided()) briefs.markOpen(ownerId, projectId, conversationId,
                answer.fieldKey(), message.getId());
            else briefs.edit(ownerId, projectId, conversationId, answer.fieldKey(), answer.value(),
                answer.decisionStatus(), message.getId());
        }
        IdeaConversation conversation = conversationRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId).orElseThrow();
        IdeaIntakeAiService.TaskView task = ai.start(ownerId, projectId, conversation, message);
        return new MessageAccepted(IdeaMessageContract.view(mapper, message), task.jobId(), task.status());
    }

    private WorkspaceView view(Long ownerId, Long projectId, IdeaConversation conversation) {
        List<IdeaMessageContract.View> messageViews = conversations.messages(ownerId, projectId, conversation.getId())
            .stream().map(value -> IdeaMessageContract.view(mapper, value)).toList();
        var brief = briefs.current(ownerId, projectId, conversation.getId());
        var attachmentViews = attachments.list(ownerId, projectId, conversation.getId());
        Set<String> messageIds = messageViews.stream().map(value -> Long.toString(value.id())).collect(java.util.stream.Collectors.toSet());
        List<TaskRun> jobs = taskRuns.findByProjectIdAndSubjectTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, "IDEA_CONVERSATION_MESSAGE").stream().filter(run -> messageIds.contains(run.getSubjectId())).toList();
        String activeJobId = jobs.stream().filter(run -> !run.terminal()).map(TaskRun::getId).findFirst()
            .orElseGet(() -> attachmentViews.stream()
                .filter(value -> Set.of("UPLOADED", "PROCESSING").contains(value.status()))
                .map(IdeaAttachmentService.AttachmentView::jobId).findFirst().orElse(null));
        String state = state(messageViews, brief, jobs);
        IdeaSource source = conversation.getSource();
        LegacySource legacy = source == null ? null : new LegacySource(source.getId(), source.getSourceType().name(),
            source.getTitle(), source.getOriginalFileReference());
        return new WorkspaceView(conversation.getId(), conversation.getStatus().name(), state,
            conversation.getCreatedAt(), messageViews, attachmentViews, brief, activeJobId, legacy);
    }

    private String state(List<IdeaMessageContract.View> messages,
            OpportunityBriefWorkspaceService.BriefView brief, List<TaskRun> jobs) {
        if (jobs.stream().anyMatch(run -> !run.terminal())) return "PROCESSING";
        if (!jobs.isEmpty() && Set.of(TaskRunState.FAILED, TaskRunState.TIMED_OUT).contains(jobs.get(0).getState())) return "FAILED";
        if (brief != null && "CONFIRMED".equals(brief.state())) return "CONFIRMED";
        if (messages.isEmpty() && brief == null) return "EMPTY";
        if (brief == null) return "DRAFT";
        if (brief.missingFields().isEmpty()) return "READY_FOR_CONFIRMATION";
        return messages.stream().reduce((first, second) -> second)
            .filter(value -> "NEEDS_INPUT".equals(value.readiness())).map(value -> "NEEDS_INPUT").orElse("DRAFT");
    }

    public OpportunityBriefWorkspaceService.BriefView confirmBrief(Long ownerId, Long projectId,
            Long conversationId) {
        List<String> contradictions = conversations.messages(ownerId, projectId, conversationId).stream()
            .map(value -> IdeaMessageContract.view(mapper, value)).reduce((first, second) -> second)
            .map(IdeaMessageContract.View::contradictions).orElse(List.of());
        return briefs.confirm(ownerId, projectId, conversationId, contradictions);
    }

    public record Answer(String fieldKey, JsonNode value, FieldDecisionStatus decisionStatus, boolean undecided) { }
    public record MessageAccepted(IdeaMessageContract.View message, String jobId, String jobStatus) { }
    public record LegacySource(Long id, String type, String title, String originalFileReference) { }
    public record WorkspaceView(Long id, String status, String domainState, java.time.LocalDateTime createdAt,
            List<IdeaMessageContract.View> messages, List<IdeaAttachmentService.AttachmentView> attachments,
            OpportunityBriefWorkspaceService.BriefView brief, String activeJobId, LegacySource legacySource) { }
}
