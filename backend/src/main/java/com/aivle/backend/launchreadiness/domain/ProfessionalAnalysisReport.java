package com.aivle.backend.launchreadiness.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "professional_launch_readiness_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessionalAnalysisReport extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(name = "module_type", nullable = false, length = 24) private ModuleType moduleType;
    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT") private String inputJson;
    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT") private String analysisJson;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    public enum ModuleType { TECHNOLOGY, OPERATIONS }
    public static ProfessionalAnalysisReport create(String id, Long projectId, ModuleType type, String input, String analysis, Long userId, Instant completedAt) {
        ProfessionalAnalysisReport value = new ProfessionalAnalysisReport();
        value.id=id; value.projectId=projectId; value.moduleType=type; value.inputJson=input; value.analysisJson=analysis; value.createdByUserId=userId; value.completedAt=completedAt;
        return value;
    }
}
