package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idea_origin_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdeaOriginVersion extends BaseEntity {
    public enum State { DRAFT, CONFIRMED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_id", nullable = false) private IdeaSource source;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_idea_version_id", nullable = false) private IdeaVersion sourceIdeaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "based_on_origin_version_id") private IdeaOriginVersion basedOnOriginVersion;
    @Column(nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(name = "confirmed_values_json", nullable = false, columnDefinition = "TEXT") private String confirmedValuesJson;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(name = "missing_fields_json", nullable = false, columnDefinition = "TEXT") private String missingFieldsJson;
    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT") private String metadataJson;
    private LocalDateTime confirmedAt;

    public static IdeaOriginVersion draft(Project project, IdeaSource source, IdeaVersion sourceIdeaVersion,
            int number, String snapshot, String confirmedValues, String assumptions, String missingFields,
            String metadata) {
        IdeaOriginVersion value = new IdeaOriginVersion();
        value.project = project; value.source = source; value.sourceIdeaVersion = sourceIdeaVersion;
        value.versionNumber = number; value.state = State.DRAFT; value.snapshotJson = snapshot;
        value.confirmedValuesJson = confirmedValues; value.assumptionsJson = assumptions;
        value.missingFieldsJson = missingFields; value.metadataJson = metadata;
        return value;
    }

    public static IdeaOriginVersion confirmed(IdeaOriginVersion draft, int number, String snapshot,
            String confirmedValues, String assumptions, String missingFields, String metadata) {
        IdeaOriginVersion value = new IdeaOriginVersion();
        value.project = draft.getProject(); value.source = draft.getSource();
        value.sourceIdeaVersion = draft.getSourceIdeaVersion();
        value.basedOnOriginVersion = draft; value.versionNumber = number; value.state = State.CONFIRMED;
        value.snapshotJson = snapshot; value.confirmedValuesJson = confirmedValues;
        value.assumptionsJson = assumptions; value.missingFieldsJson = missingFields;
        value.metadataJson = metadata; value.confirmedAt = LocalDateTime.now();
        return value;
    }
}
