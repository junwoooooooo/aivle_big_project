package com.aivle.backend.marketing.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.file.entity.StoredFile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "marketing_assets")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingAsset extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "marketing_material_id", nullable = false) private MarketingMaterial marketingMaterial;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "stored_file_id", nullable = false) private StoredFile storedFile;
    @Column(nullable = false, length = 50) private String assetType;
}
