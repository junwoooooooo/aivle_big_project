package com.aivle.backend.persona.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "persona_instances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaInstance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private PersonaSegment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    private Integer age;

    @Column(length = 10)
    private String gender;

    @Column(length = 50)
    private String occupation;

    @Column(columnDefinition = "TEXT")
    private String background;

    @Column(columnDefinition = "TEXT")
    private String traitsJson;

    @Column(nullable = false)
    private Boolean isSystem;

    @Column(nullable = false)
    private Boolean active;
}
