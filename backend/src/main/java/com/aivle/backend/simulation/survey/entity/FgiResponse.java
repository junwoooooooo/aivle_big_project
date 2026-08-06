package com.aivle.backend.simulation.survey.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.persona.entity.PersonaInstance;
import com.aivle.backend.simulation.entity.Simulation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "fgi_responses")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FgiResponse extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "simulation_id", nullable = false) private Simulation simulation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id", nullable = false) private FgiQuestion question;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "persona_id", nullable = false) private PersonaInstance persona;
    @Column(columnDefinition = "TEXT") private String answerText;
    @Column(precision = 7, scale = 2) private BigDecimal score;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Sentiment sentiment;
    @Column(columnDefinition = "TEXT") private String keywordsJson;
    @Column(nullable = false) private Boolean followUp;
}
