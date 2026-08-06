package com.aivle.backend.simulation.entity;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "prediction_metrics")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PredictionMetric extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @Column(nullable = false, length = 100) private String metricKey;
    @Column(nullable = false, length = 150) private String label;
    @Column(name = "metric_value", precision = 19, scale = 4) private BigDecimal value;
    @Column(length = 50) private String unit;
    @Column(columnDefinition = "TEXT") private String segmentBreakdownJson;
}
