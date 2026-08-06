package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "concept_versions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptVersion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "concept_id", nullable = false) private Concept concept;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, columnDefinition = "TEXT") private String oneLineSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String targetCustomer;
    @Column(nullable = false, columnDefinition = "TEXT") private String problem;
    @Column(nullable = false, columnDefinition = "TEXT") private String solution;
    @Column(nullable = false, columnDefinition = "TEXT") private String valueProposition;
    @Column(nullable = false, columnDefinition = "TEXT") private String revenueModel;
    @Column(name = "key_features_json", nullable = false, columnDefinition = "TEXT") private String keyFeaturesJson;
    @Column(name = "differentiators_json", nullable = false, columnDefinition = "TEXT") private String differentiatorsJson;
    @Column(name = "assumptions_json", nullable = false, columnDefinition = "TEXT") private String assumptionsJson;
    @Column(name = "risks_json", nullable = false, columnDefinition = "TEXT") private String risksJson;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "eligibility_batch_id") private ConceptEligibilityBatch eligibilityBatch;
    @Column(nullable = false, length = 20) private String eligibilityStatus = "LEGACY";
    @Column(nullable = false, columnDefinition = "TEXT") private String targetSegmentJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT") private String positioning = "";
    @Column(nullable = false, columnDefinition = "TEXT") private String pricingJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT") private String channelsJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT") private String operatingModelJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT") private String newBusinessActivitiesJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT") private String originTraceJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT") private String legalTraceJson = "[]";
    public static ConceptVersion create(Project project, IdeaVersion ideaVersion, Concept concept, String name,
            String summary, String customer, String problem, String solution, String value, String revenue,
            String features, String differentiators, String assumptions, String risks) {
        ConceptVersion v = new ConceptVersion(); v.project = project; v.ideaVersion = ideaVersion; v.concept = concept; v.versionNumber = 1;
        v.name = name; v.oneLineSummary = summary; v.targetCustomer = customer; v.problem = problem; v.solution = solution;
        v.valueProposition = value; v.revenueModel = revenue; v.keyFeaturesJson = features;
        v.differentiatorsJson = differentiators; v.assumptionsJson = assumptions; v.risksJson = risks; return v;
    }
    public static ConceptVersion eligible(Project project, IdeaVersion ideaVersion, Concept concept,
            ConceptEligibilityBatch batch, String name, String targetSegment, String positioning,
            String features, String pricing, String revenue, String channels, String operating,
            String assumptions, String activities, String originTrace, String legalTrace) {
        ConceptVersion value = new ConceptVersion(); value.project=project; value.ideaVersion=ideaVersion;
        value.concept=concept; value.eligibilityBatch=batch; value.eligibilityStatus="ELIGIBLE"; value.versionNumber=1;
        value.name=name; value.oneLineSummary=positioning; value.targetCustomer=targetSegment;
        value.problem=originTrace; value.solution=features; value.valueProposition=positioning;
        value.revenueModel=revenue; value.keyFeaturesJson=features; value.differentiatorsJson=channels;
        value.assumptionsJson=assumptions; value.risksJson="[]"; value.targetSegmentJson=targetSegment;
        value.positioning=positioning; value.pricingJson=pricing; value.channelsJson=channels;
        value.operatingModelJson=operating; value.newBusinessActivitiesJson=activities;
        value.originTraceJson=originTrace; value.legalTraceJson=legalTrace; return value;
    }
}
