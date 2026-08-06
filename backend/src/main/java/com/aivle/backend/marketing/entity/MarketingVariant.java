package com.aivle.backend.marketing.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.file.entity.StoredFile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "marketing_variants", uniqueConstraints = @UniqueConstraint(name = "uk_marketing_variant", columnNames = {"marketing_material_id", "variant_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingVariant extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "marketing_material_id", nullable = false) private MarketingMaterial marketingMaterial;
    @Column(nullable = false) private Integer variantNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String contentJson;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "stored_file_id") private StoredFile storedFile;
    @Column(precision = 5, scale = 2) private BigDecimal score;
}
