package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "legal_guardrail_sets") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalGuardrailSet extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "legal_precheck_version_id", nullable = false) private LegalPrecheckVersion legalPrecheckVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_run_id", nullable = false) private LegalPrecheckRun sourceRun;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String hardConstraintsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String prohibitedPatternsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String conditionalConstraintsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String requiredDisclosuresJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String requiredOperationalControlsJson;

    public static LegalGuardrailSet create(Project project, LegalPrecheckVersion version, LegalPrecheckRun run,
            int number, String hard, String prohibited, String conditional, String disclosures, String controls) {
        LegalGuardrailSet value = new LegalGuardrailSet(); value.project = project; value.legalPrecheckVersion = version;
        value.sourceRun = run; value.versionNumber = number; value.hardConstraintsJson = hard;
        value.prohibitedPatternsJson = prohibited; value.conditionalConstraintsJson = conditional;
        value.requiredDisclosuresJson = disclosures; value.requiredOperationalControlsJson = controls;
        return value;
    }
}
