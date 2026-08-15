package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * BM 실행 계획 준비 — 사용자가 BM 캔버스 앞에서 채우는 칸.
 *
 * <p>계획 4칸(핵심 활동·핵심 자원·핵심 파트너·고객 관계)은 <b>컨셉 계약이 주지 않는
 * 값</b>이다. 입구계약서 §1 의 선택 필드는 {@code region}·{@code price_hypothesis_krw}·
 * {@code constraint}·{@code _다듬기5}·{@code _경쟁_씨앗} 뿐이고 거기에 이 넷이 없다.
 * 견본 컨셉의 {@code _bm_plan} 은 계약 밖의 손으로 쓴 스텁이라 견본 3개 중 하나에만 있다.
 *
 * <p>모양은 {@code FinancialInputPreparation} 을 따른다 — 같은 성질의 것(사용자가 채워
 * 두고 나중에 하류 실행이 읽는 준비물)이라 새 형태를 만들지 않는다.
 *
 * <p>⚠ {@code @Lob} 를 쓰지 않는다. Postgres 에서 {@code @Lob String} 은 {@code oid} 를
 * 기대하는데 마이그레이션은 {@code TEXT} 로 만들어 {@code ddl-auto=validate} 가 부팅에서
 * 죽는다({@code MarketResearchVersion} 이 같은 이유로 같은 주석을 달고 있다).
 */
@Entity
@Table(name = "bm_plan_preparations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BmPlanPreparation extends BaseEntity {

    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT") private String planJson;
    @Column(name = "constraint_json", nullable = false, columnDefinition = "TEXT") private String constraintJson;
    @Column(nullable = false) private int revision;
    @Column(name = "updated_by_user_id", nullable = false) private Long updatedByUserId;

    public static BmPlanPreparation create(String id, Long projectId, String planJson,
                                           String constraintJson, Long userId) {
        if (blank(id) || projectId == null || blank(planJson) || blank(constraintJson)
                || userId == null) {
            throw new IllegalArgumentException("BM 실행 계획 준비값이 올바르지 않습니다.");
        }
        BmPlanPreparation value = new BmPlanPreparation();
        value.id = id;
        value.projectId = projectId;
        value.planJson = planJson;
        value.constraintJson = constraintJson;
        value.revision = 1;
        value.updatedByUserId = userId;
        return value;
    }

    /** 저장할 때마다 {@code revision} 이 오른다 — 누가 언제 무엇을 바꿨는지의 최소 기록이다. */
    public void update(String planJson, String constraintJson, Long userId) {
        if (blank(planJson) || blank(constraintJson) || userId == null) {
            throw new IllegalArgumentException("BM 실행 계획 값이 올바르지 않습니다.");
        }
        this.planJson = planJson;
        this.constraintJson = constraintJson;
        this.updatedByUserId = userId;
        this.revision++;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
