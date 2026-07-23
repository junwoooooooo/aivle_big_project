package com.aivle.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "personas")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Persona extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // NULL 가능 (시스템 기본 페르소나인 경우)

    @Column(nullable = false, length = 50)
    private String name;

    private Integer age;

    @Column(length = 10)
    private String gender;

    @Column(length = 50)
    private String occupation;

    @Column(length = 50)
    private String ageGroup;

    @Column(columnDefinition = "TEXT")
    private String traits; // 소비성향 및 라이프스타일

    @Column(columnDefinition = "TEXT")
    private String systemPrompt; // LLM 주입용 페르소나 프롬프트

    private Boolean isSystem; // 시스템 기본제공 여부
}
