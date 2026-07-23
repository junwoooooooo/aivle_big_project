package com.aivle.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feasibility_analyses")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeasibilityAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 30)
    private String stepType; // "LEGAL", "MARKET", "BM", "TECH", "FINANCE"

    @Column(nullable = false)
    private Integer gateStatus; // 0: 보류, 1: 승인, 2: 반려

    @Column(columnDefinition = "TEXT")
    private String inputParams; // AI 분석 Raw 데이터/파라미터

    @Column(columnDefinition = "TEXT")
    private String resultData; // 리포트 템플릿용 구조화(JSON) 데이터
}
