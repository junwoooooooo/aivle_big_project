package com.aivle.backend.project.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_services")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductService extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(precision = 19, scale = 2)
    private BigDecimal targetPrice;

    @Column(length = 255)
    private String targetAudience;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String featuresJson;

    @Column(columnDefinition = "TEXT")
    private String valueProposition;

    @Column(nullable = false)
    private Boolean active;
}
