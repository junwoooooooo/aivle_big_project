package com.aivle.backend.pipeline.conceptportfolio.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_input_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptInputResponse extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "input_request_id", nullable = false) private ConceptInputRequest inputRequest;
    @Column(nullable = false, columnDefinition = "TEXT") private String responseJson;
    @Column(nullable = false, length = 128) private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responded_by_user_id", nullable = false) private User respondedByUser;

    public static ConceptInputResponse create(ConceptInputRequest request, User user,
            String responseJson, String idempotencyKey) {
        if (request == null || user == null || responseJson == null || responseJson.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Concept InputResponse is invalid");
        }
        ConceptInputResponse value = new ConceptInputResponse();
        value.id = UUID.randomUUID().toString();
        value.project = request.getProject();
        value.inputRequest = request;
        value.responseJson = responseJson;
        value.idempotencyKey = idempotencyKey;
        value.respondedByUser = user;
        return value;
    }
}
