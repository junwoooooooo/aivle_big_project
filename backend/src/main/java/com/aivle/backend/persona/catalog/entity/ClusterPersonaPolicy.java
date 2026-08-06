package com.aivle.backend.persona.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "cluster_persona_policies",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cluster_persona_policy_persona",
        columnNames = "baseline_persona_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusterPersonaPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baseline_persona_id", nullable = false)
    private BaselinePersona persona;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int displayOrder;

    private Long updatedByUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static ClusterPersonaPolicy create(
        BaselinePersona persona,
        boolean enabled,
        int displayOrder,
        Long updatedByUserId,
        LocalDateTime now
    ) {
        ClusterPersonaPolicy value = new ClusterPersonaPolicy();
        value.persona = persona;
        value.enabled = enabled;
        value.displayOrder = displayOrder;
        value.updatedByUserId = updatedByUserId;
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    public void updateVisibility(boolean enabled, Long actorUserId, LocalDateTime now) {
        this.enabled = enabled;
        this.updatedByUserId = actorUserId;
        this.updatedAt = now;
    }

    public void reorder(int displayOrder, Long actorUserId, LocalDateTime now) {
        this.displayOrder = displayOrder;
        this.updatedByUserId = actorUserId;
        this.updatedAt = now;
    }
}
