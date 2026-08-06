package com.aivle.backend.simulation.survey.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.simulation.entity.Simulation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "fgi_questions")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FgiQuestion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @Column(nullable = false) private Integer questionOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private QuestionType questionType;
    @Column(length = 100) private String evaluationAxis;
    @Column(nullable = false, columnDefinition = "TEXT") private String questionText;
    @Column(columnDefinition = "TEXT") private String optionsJson;
    @Column(nullable = false) private Boolean required;
}
