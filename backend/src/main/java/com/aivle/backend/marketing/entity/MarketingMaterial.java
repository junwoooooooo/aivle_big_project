package com.aivle.backend.marketing.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.persona.entity.PersonaSegment;
import com.aivle.backend.project.entity.ProductService;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "marketing_materials")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingMaterial extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_service_id") private ProductService productService;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private MarketingMaterialType materialType;
    @Column(nullable = false, length = 200) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobStatus status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_persona_segment_id") private PersonaSegment targetPersonaSegment;
    @Column(columnDefinition = "TEXT") private String prompt;
    @Column(columnDefinition = "TEXT") private String contentJson;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "representative_file_id") private StoredFile representativeFile;
}
