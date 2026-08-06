package com.aivle.backend.pipeline.selection.application;

import static com.aivle.backend.pipeline.selection.api.SelectionApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalEvidenceLinkRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ConceptSelectionService {
    private final ProjectRepository projects;
    private final ConceptFactoryRunRepository runs;
    private final ConceptRepository concepts;
    private final ConceptLegalAssessmentRepository assessments;
    private final ConceptLegalEvidenceLinkRepository evidenceLinks;
    private final ConceptSelectionRepository selections;
    private final SelectedConceptSnapshotRepository snapshots;
    private final SnapshotHasher snapshotHasher;
    private final ObjectMapper mapper;

    @Transactional
    public SelectionResponse select(Long ownerId, Long projectId, CreateSelectionRequest request) {
        var project = projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var run = runs.findCurrentOwned(ownerId, projectId)
            .filter(value -> value.getStatus() == ConceptFactoryRunStatus.COMPLETED)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        Concept concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(request.conceptId(), projectId)
            .filter(value -> value.getRun().getId().equals(run.getId()) && value.getSourceSnapshotHash().equals(run.getSourceSnapshotHash()))
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        String reason = request.selectionReason().strip();
        String requestHash = SelectionRequestFingerprint.create(concept.getId(), concept.getCanonicalHash(), reason);
        var idempotent = selections.findByProjectIdAndRequestHashAndCurrentSelectionTrueAndDeletedAtIsNull(projectId, requestHash);
        if (idempotent.isPresent()) return response(idempotent.get());

        SelectedConceptSnapshot previous = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(projectId).orElse(null);
        selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).ifPresent(ConceptSelection::supersede);
        selections.flush();
        Instant selectedAt = Instant.now();
        ConceptSelection selection = selections.save(ConceptSelection.select(projectId, concept.getId(), reason, requestHash, ownerId, selectedAt));
        var assessment = assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), projectId)
            .orElseThrow(() -> new IllegalStateException("selected concept requires a legal assessment"));
        String snapshotId = UUID.randomUUID().toString();
        int sequence = previous == null ? 1 : previous.getSequence() + 1;
        ObjectNode body = snapshotBody(snapshotId, selection, concept, assessment.getAssessmentJson(), assessment.getStatus().name(),
            assessment.getSafeSummary(), selectedAt, sequence, previous == null ? null : previous.getId());
        String snapshotHash = snapshotHasher.hash(body);
        SelectedConceptSnapshot snapshot = snapshots.save(SelectedConceptSnapshot.create(snapshotId, selection, sequence,
            previous == null ? null : previous.getId(), concept.getCanonicalHash(), snapshotHash,
            mapper.writeValueAsString(body), ownerId, selectedAt));
        return response(selection, snapshot);
    }

    @Transactional(readOnly = true)
    public SelectionResponse current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        ConceptSelection selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "현재 컨셉 선택이 없습니다."));
        return response(selection);
    }

    private ObjectNode snapshotBody(String snapshotId, ConceptSelection selection, Concept concept, String assessmentJson,
                                    String legalStatus, String safeSummary, Instant selectedAt, int sequence, String parentId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("snapshotContract", "selected-concept-snapshot-v1");
        root.put("snapshotId", snapshotId);
        root.put("projectId", selection.getProjectId());
        root.put("selectionId", selection.getId());
        root.put("sequence", sequence);
        if (parentId == null) root.putNull("parentSnapshotId"); else root.put("parentSnapshotId", parentId);
        root.put("selectedAt", selectedAt.toString());
        root.put("selectionReason", selection.getSelectionReason());
        ObjectNode conceptNode = root.putObject("concept");
        conceptNode.put("conceptId", concept.getId());
        conceptNode.put("title", concept.getTitle());
        conceptNode.put("summary", concept.getSummary());
        conceptNode.put("sourceIdeaBriefSnapshotId", concept.getSourceIdeaBriefSnapshotId());
        conceptNode.put("sourceSnapshotHash", concept.getSourceSnapshotHash());
        conceptNode.put("canonicalHash", concept.getCanonicalHash());
        conceptNode.set("planning", mapper.readTree(concept.getCandidateJson()));
        ObjectNode legalNode = root.putObject("legalAssessment");
        legalNode.put("status", legalStatus);
        legalNode.put("safeSummary", safeSummary);
        JsonNode assessment = mapper.readTree(assessmentJson);
        legalNode.set("assessment", assessment);
        copy(assessment, legalNode, "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants");
        var evidence = legalNode.putArray("evidenceReferences");
        evidenceLinks.findAllByAssessmentIdAndProjectIdAndDeletedAtIsNull(
            assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), selection.getProjectId()).orElseThrow().getId(), selection.getProjectId())
            .forEach(link -> {
                ObjectNode item = evidence.addObject();
                item.put("title", link.getEvidence().getTitle());
                item.put("officialSourceUri", link.getEvidence().getSourceUri());
                item.put("contentHash", link.getEvidence().getContentHash());
            });
        return root;
    }

    private void copy(JsonNode source, ObjectNode target, String... fields) {
        for (String field : fields) target.set(field, source.path(field));
    }

    private SelectionResponse response(ConceptSelection selection) {
        SelectedConceptSnapshot snapshot = snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), selection.getProjectId())
            .orElseThrow(() -> new IllegalStateException("selection snapshot is missing"));
        return response(selection, snapshot);
    }

    private SelectionResponse response(ConceptSelection selection, SelectedConceptSnapshot snapshot) {
        return new SelectionResponse(selection.getId(), selection.getConceptId(), selection.getSelectionReason(), selection.getSelectedAt(),
            selection.isCurrentSelection(), new SnapshotResponse(snapshot.getId(), snapshot.getSequence(), snapshot.getParentSnapshotId(),
            snapshot.getSnapshotHash(), snapshot.getSourceConceptHash(), snapshot.getSelectedAt(), mapper.readTree(snapshot.getSnapshotJson())));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
