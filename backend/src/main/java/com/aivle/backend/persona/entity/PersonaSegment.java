package com.aivle.backend.persona.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "persona_segments")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaSegment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String segmentCode;
    @Column(nullable = false, length = 150) private String segmentName;
    @Column(length = 50) private String ageGroup;
    @Column(length = 20) private String gender;
    private Integer sampleCount;
    @Column(precision = 7, scale = 4) private BigDecimal weightedShare;
    @Column(columnDefinition = "TEXT") private String traitsJson;
    @Column(length = 200) private String sourceDataset;
    @Column(length = 100) private String clusterModelVersion;
    @Column(nullable = false) private Boolean active;
}
