package com.aivle.backend.persona.catalog.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "baseline_personas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_baseline_persona_code_catalog",
        columnNames = {"persona_code", "catalog_version"}),
    @UniqueConstraint(name = "uk_baseline_persona_cluster_catalog",
        columnNames = {"cluster_id", "catalog_version"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaselinePersona extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String personaCode;
    @Column(nullable = false, length = 100) private String clusterId;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(nullable = false, length = 120) private String shortName;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(nullable = false, length = 40) private String ageGroup;
    @Column(nullable = false, length = 20) private String gender;
    private Integer sampleSize;
    @Column(precision = 7, scale = 4) private BigDecimal weightedShare;
    @Column(nullable = false, length = 200) private String dataSource;
    @Column(nullable = false, length = 100) private String dataVersion;
    @Column(nullable = false, length = 100) private String catalogVersion;
    @Column(nullable = false, columnDefinition = "TEXT") private String keyTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String consumptionTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String technologyTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String channelTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String subscriptionTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String financialTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String aiUsageTraitsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String demographicSummaryJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String evidenceMetricsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String limitationsJson;
    @Column(nullable = false, length = 64) private String sourceHash;
    @Column(nullable = false) private Integer displayOrder;

    public static BaselinePersona imported(
        String personaCode, String clusterId, String displayName, String shortName,
        String description, String ageGroup, String gender, BigDecimal weightedShare,
        String dataSource, String dataVersion, String catalogVersion,
        String keyTraitsJson, String demographicSummaryJson, String evidenceMetricsJson,
        String limitationsJson, String sourceHash, int displayOrder
    ) {
        BaselinePersona value = new BaselinePersona();
        value.personaCode = personaCode;
        value.clusterId = clusterId;
        value.displayName = displayName;
        value.shortName = shortName;
        value.description = description;
        value.ageGroup = ageGroup;
        value.gender = gender;
        value.sampleSize = null;
        value.weightedShare = weightedShare;
        value.dataSource = dataSource;
        value.dataVersion = dataVersion;
        value.catalogVersion = catalogVersion;
        value.keyTraitsJson = keyTraitsJson;
        value.consumptionTraitsJson = "[]";
        value.technologyTraitsJson = "[]";
        value.channelTraitsJson = "[]";
        value.subscriptionTraitsJson = "[]";
        value.financialTraitsJson = "[]";
        value.aiUsageTraitsJson = "[]";
        value.demographicSummaryJson = demographicSummaryJson;
        value.evidenceMetricsJson = evidenceMetricsJson;
        value.limitationsJson = limitationsJson;
        value.sourceHash = sourceHash;
        value.displayOrder = displayOrder;
        return value;
    }
}
