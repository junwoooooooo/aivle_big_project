package com.aivle.backend.journey.brief;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.journey.conversation.IdeaConversationRepository;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.aivle.backend.taskrun.domain.TaskRun;

@Service
public class OpportunityBriefWorkspaceService {
    public static final List<String> FIELD_KEYS = List.of(
        "problem", "targetCustomer", "beneficiaries", "usageContext", "desiredOutcome", "targetRegion",
        "fixedConstraints", "preferredConstraints", "openDecisions", "assumptions", "prohibitedApproaches",
        "regulatorySensitiveActivities"
    );
    private static final Set<String> REQUIRED = Set.of(
        "problem", "desiredOutcome", "targetRegion", "fixedConstraints", "openDecisions",
        "regulatorySensitiveActivities"
    );

    private final FoundationProjectAccess projectAccess;
    private final IdeaConversationRepository conversations;
    private final OpportunityBriefVersionRepository versions;
    private final OpportunityFieldValueRepository fields;
    private final OpportunityBriefService foundation;
    private final ObjectMapper mapper;

    public OpportunityBriefWorkspaceService(FoundationProjectAccess projectAccess,
            IdeaConversationRepository conversations, OpportunityBriefVersionRepository versions,
            OpportunityFieldValueRepository fields, OpportunityBriefService foundation, ObjectMapper mapper) {
        this.projectAccess = projectAccess;
        this.conversations = conversations;
        this.versions = versions;
        this.fields = fields;
        this.foundation = foundation;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public BriefView current(Long ownerId, Long projectId, Long conversationId) {
        projectAccess.requireOwned(ownerId, projectId);
        requireConversation(projectId, conversationId);
        return versions.findTopByProjectIdAndConversationIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, conversationId)
            .map(this::view).orElse(null);
    }

    @Transactional
    public BriefView edit(Long ownerId, Long projectId, Long conversationId, String fieldKey,
            JsonNode value, FieldDecisionStatus decisionStatus, Long sourceMessageId) {
        requireFieldKey(fieldKey);
        if (value == null || value.isNull()) throw new IllegalArgumentException("field value is required");
        Map<String, FieldState> merged = currentStates(projectId, conversationId);
        merged.put(fieldKey, new FieldState(fieldKey, value, requireStatus(decisionStatus),
            FieldSourceType.USER_CONFIRMED, provenance(sourceMessageId, null, null, true, LocalDateTime.now()),
            sourceMessageId, null, null, true, LocalDateTime.now()));
        return view(create(ownerId, projectId, conversationId, merged));
    }

    @Transactional
    public BriefView adopt(Long ownerId, Long projectId, Long conversationId, String fieldKey) {
        requireFieldKey(fieldKey);
        Map<String, FieldState> merged = currentStates(projectId, conversationId);
        FieldState current = merged.get(fieldKey);
        if (current == null || current.value() == null) throw new IllegalArgumentException("suggested field is missing");
        merged.put(fieldKey, new FieldState(fieldKey, current.value(), current.decisionStatus(),
            FieldSourceType.USER_CONFIRMED, provenance(current.sourceMessageId(), current.sourceAttachmentId(),
                current.confidence(), true, LocalDateTime.now()), current.sourceMessageId(), current.sourceAttachmentId(),
                current.confidence(), true, LocalDateTime.now()));
        return view(create(ownerId, projectId, conversationId, merged));
    }

    @Transactional
    public BriefView reject(Long ownerId, Long projectId, Long conversationId, String fieldKey) {
        return markMissing(ownerId, projectId, conversationId, fieldKey, null, false);
    }

    @Transactional
    public BriefView markOpen(Long ownerId, Long projectId, Long conversationId, String fieldKey,
            Long sourceMessageId) {
        return markMissing(ownerId, projectId, conversationId, fieldKey, sourceMessageId, true);
    }

    private BriefView markMissing(Long ownerId, Long projectId, Long conversationId, String fieldKey,
            Long sourceMessageId, boolean userConfirmed) {
        requireFieldKey(fieldKey);
        Map<String, FieldState> merged = currentStates(projectId, conversationId);
        FieldState current = merged.get(fieldKey);
        FieldDecisionStatus status = current == null ? FieldDecisionStatus.OPEN : current.decisionStatus();
        merged.put(fieldKey, new FieldState(fieldKey, null, status, FieldSourceType.MISSING,
            provenance(sourceMessageId, null, null, userConfirmed, userConfirmed ? LocalDateTime.now() : null),
            sourceMessageId, null, null, userConfirmed, userConfirmed ? LocalDateTime.now() : null));
        return view(create(ownerId, projectId, conversationId, merged));
    }

    @Transactional
    public BriefView mergeAiDraft(Long ownerId, Long projectId, Long conversationId,
            List<AiField> proposals, Long sourceMessageId, Long sourceAttachmentId) {
        return mergeAiDraft(ownerId, projectId, conversationId, proposals, sourceMessageId, sourceAttachmentId, null);
    }

    @Transactional
    public BriefView mergeAiDraft(Long ownerId, Long projectId, Long conversationId,
            List<AiField> proposals, Long sourceMessageId, Long sourceAttachmentId, TaskRun taskRun) {
        Map<String, FieldState> merged = currentStates(projectId, conversationId);
        for (AiField proposal : proposals == null ? List.<AiField>of() : proposals) {
            requireFieldKey(proposal.fieldKey());
            if (proposal.sourceType() != FieldSourceType.AI_PROPOSED
                    && proposal.sourceType() != FieldSourceType.SOURCE_EXTRACTED
                    && proposal.sourceType() != FieldSourceType.MISSING) {
                throw new IllegalArgumentException("AI field source is not permitted");
            }
            if (proposal.decisionStatus() == FieldDecisionStatus.LOCKED) {
                throw new IllegalArgumentException("AI cannot lock an opportunity field");
            }
            if (proposal.sourceType() == FieldSourceType.MISSING && proposal.value() != null && !proposal.value().isNull()) {
                throw new IllegalArgumentException("missing AI field cannot contain a value");
            }
            if (proposal.sourceType() != FieldSourceType.MISSING && (proposal.value() == null || proposal.value().isNull())) {
                throw new IllegalArgumentException("AI field value is required");
            }
            FieldState existing = merged.get(proposal.fieldKey());
            if (existing != null && existing.userConfirmed()) continue;
            merged.put(proposal.fieldKey(), new FieldState(proposal.fieldKey(), proposal.value(),
                requireStatus(proposal.decisionStatus()), proposal.sourceType(),
                provenance(sourceMessageId, sourceAttachmentId, proposal.confidence(), false, null),
                sourceMessageId, sourceAttachmentId, proposal.confidence(), false, null));
        }
        return view(create(ownerId, projectId, conversationId, merged, taskRun));
    }

    @Transactional
    public BriefView confirm(Long ownerId, Long projectId, Long conversationId, List<String> fatalContradictions) {
        Map<String, FieldState> states = currentStates(projectId, conversationId);
        List<String> missing = missing(states);
        if (fatalContradictions != null && !fatalContradictions.isEmpty()) missing.add("contradictions");
        if (!missing.isEmpty()) throw new BriefIncompleteException(missing);
        LocalDateTime now = LocalDateTime.now();
        Map<String, FieldState> confirmed = new LinkedHashMap<>();
        states.forEach((key, value) -> confirmed.put(key, new FieldState(key, value.value(), value.decisionStatus(),
            value.sourceType(), provenance(value.sourceMessageId(), value.sourceAttachmentId(), value.confidence(), true, now),
            value.sourceMessageId(), value.sourceAttachmentId(), value.confidence(), true, now)));
        OpportunityBriefVersion version = create(ownerId, projectId, conversationId, confirmed);
        return view(foundation.confirm(ownerId, projectId, version.getId()));
    }

    public List<String> missing(Map<String, FieldState> states) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        REQUIRED.forEach(key -> { if (!present(states.get(key))) missing.add(key); });
        if (!present(states.get("targetCustomer")) && !present(states.get("beneficiaries"))) {
            missing.add("targetCustomerOrBeneficiaries");
        }
        return new ArrayList<>(missing);
    }

    private boolean present(FieldState value) {
        return value != null && value.value() != null && !value.value().isNull()
            && !(value.value().isTextual() && value.value().asText().isBlank());
    }

    private OpportunityBriefVersion create(Long ownerId, Long projectId, Long conversationId,
            Map<String, FieldState> states) {
        return create(ownerId, projectId, conversationId, states, null);
    }

    private OpportunityBriefVersion create(Long ownerId, Long projectId, Long conversationId,
            Map<String, FieldState> states, TaskRun taskRun) {
        IdeaConversation conversation = requireConversation(projectId, conversationId);
        OpportunityBriefVersion base = versions
            .findTopByProjectIdAndConversationIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, conversationId)
            .orElse(null);
        List<FieldState> ordered = states.values().stream()
            .sorted(Comparator.comparingInt(value -> FIELD_KEYS.indexOf(value.fieldKey()))).toList();
        ObjectNode snapshot = mapper.createObjectNode();
        ordered.forEach(value -> {
            ObjectNode field = snapshot.putObject(value.fieldKey());
            field.set("value", value.value());
            field.put("decisionStatus", value.decisionStatus().name());
            field.put("sourceType", value.sourceType().name());
            field.set("provenance", mapper.readTree(value.sourceReference()));
        });
        List<OpportunityBriefService.FieldInput> inputs = ordered.stream().map(value ->
            new OpportunityBriefService.FieldInput(value.fieldKey(),
                value.value() == null || value.value().isNull() ? null : mapper.writeValueAsString(value.value()),
                value.decisionStatus(), value.sourceType(), value.sourceReference(), value.sourceMessageId(),
                value.sourceAttachmentId(), value.confidence(), value.userConfirmed(), value.confirmedAt())).toList();
        return foundation.createDraft(ownerId, projectId, conversation.getId(),
            base == null ? null : base.getId(), mapper.writeValueAsString(snapshot), inputs, taskRun);
    }

    private Map<String, FieldState> currentStates(Long projectId, Long conversationId) {
        requireConversation(projectId, conversationId);
        OpportunityBriefVersion current = versions
            .findTopByProjectIdAndConversationIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, conversationId)
            .orElse(null);
        LinkedHashMap<String, FieldState> result = new LinkedHashMap<>();
        if (current == null) return result;
        for (OpportunityFieldValue value : fields.findByBriefVersionIdAndDeletedAtIsNullOrderByFieldKey(current.getId())) {
            result.put(value.getFieldKey(), new FieldState(value.getFieldKey(),
                value.getValueJson() == null ? null : mapper.readTree(value.getValueJson()),
                value.getDecisionStatus(), value.getSourceType(), value.getSourceReference(),
                value.getSourceMessage() == null ? null : value.getSourceMessage().getId(),
                value.getSourceAttachment() == null ? null : value.getSourceAttachment().getId(),
                value.getConfidence() == null ? null : value.getConfidence().doubleValue(),
                value.isUserConfirmed(), value.getConfirmedAt()));
        }
        return result;
    }

    private BriefView view(OpportunityBriefVersion version) {
        Map<String, FieldState> states = currentStates(version.getProject().getId(), version.getConversation().getId());
        return new BriefView(version.getId(), version.getVersionNumber(), version.getState().name(),
            version.getSnapshotHash(), version.getConfirmedAt(), List.copyOf(states.values()), missing(states));
    }

    private IdeaConversation requireConversation(Long projectId, Long conversationId) {
        return conversations.findByIdAndProjectIdAndDeletedAtIsNull(conversationId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String provenance(Long messageId, Long attachmentId, Double confidence,
            boolean confirmed, LocalDateTime confirmedAt) {
        ObjectNode node = mapper.createObjectNode();
        if (messageId != null) node.put("sourceMessageId", messageId);
        if (attachmentId != null) node.put("sourceAttachmentId", attachmentId);
        if (confidence != null) node.put("confidence", confidence);
        node.put("userConfirmed", confirmed);
        if (confirmedAt != null) node.put("confirmedAt", confirmedAt.toString());
        return mapper.writeValueAsString(node);
    }

    private void requireFieldKey(String key) { if (!FIELD_KEYS.contains(key)) throw new IllegalArgumentException("unsupported opportunity field"); }
    private FieldDecisionStatus requireStatus(FieldDecisionStatus status) {
        if (status == null) throw new IllegalArgumentException("decision status is required");
        return status;
    }

    public record AiField(String fieldKey, JsonNode value, FieldDecisionStatus decisionStatus,
                          FieldSourceType sourceType, Double confidence) { }
    public record FieldState(String fieldKey, JsonNode value, FieldDecisionStatus decisionStatus,
            FieldSourceType sourceType, String sourceReference, Long sourceMessageId,
            Long sourceAttachmentId, Double confidence, boolean userConfirmed, LocalDateTime confirmedAt) {
        FieldState(String fieldKey, JsonNode value, FieldDecisionStatus decisionStatus,
                FieldSourceType sourceType, String sourceReference) {
            this(fieldKey, value, decisionStatus, sourceType, sourceReference, null, null, null,
                false, null);
        }
    }
    public record BriefView(Long id, int version, String state, String hash,
            LocalDateTime confirmedAt, List<FieldState> fields, List<String> missingFields) { }
    public static class BriefIncompleteException extends RuntimeException {
        private final List<String> missingFields;
        public BriefIncompleteException(List<String> missingFields) {
            super("Opportunity brief is incomplete"); this.missingFields = List.copyOf(missingFields);
        }
        public List<String> missingFields() { return missingFields; }
    }
}
