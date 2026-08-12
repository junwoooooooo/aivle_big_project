# AIDEV-RESYNC R1 사용자 검증

## 1. Migration preflight

실제 운영 PostgreSQL에서 V20 적용 전에 아래 두 쿼리의 결과가 모두 0행인지 확인한다.

```sql
SELECT project_id,
       source_market_research_version_id,
       source_business_model_version_id,
       COUNT(*) AS duplicate_count,
       ARRAY_AGG(id ORDER BY created_at, id) AS row_ids
FROM financial_input_preparations
WHERE deleted_at IS NULL
  AND source_market_research_version_id IS NOT NULL
  AND source_business_model_version_id IS NOT NULL
GROUP BY project_id, source_market_research_version_id, source_business_model_version_id
HAVING COUNT(*) > 1;
```

```sql
SELECT project_id,
       source_market_research_version_id,
       source_business_model_version_id,
       COUNT(*) AS duplicate_count,
       ARRAY_AGG(id ORDER BY finalized_at, id) AS row_ids
FROM financial_input_snapshots
WHERE deleted_at IS NULL
  AND source_market_research_version_id IS NOT NULL
  AND source_business_model_version_id IS NOT NULL
GROUP BY project_id, source_market_research_version_id, source_business_model_version_id
HAVING COUNT(*) > 1;
```

결과가 있으면 migration을 중단한다. 자동 `DELETE`를 추가하지 않는다. 각 row의 provenance·사용자 결정·Snapshot history를 검토해 보존할 current row를 정하고, 대체된 row는 별도 감사 트랜잭션에서 soft delete한 뒤 V20을 다시 적용한다. 모든 원본과 history는 보존한다.

V21 전에는 기존 legacy row가 다음 조건을 만족하는지 확인한다. 기존 schema의 NOT NULL 및 project-scoped FK가 이미 보장하지만 운영 drift 탐지를 위해 실행한다.

```sql
SELECT source.id, source.project_id, source.selection_id, source.concept_id
FROM marketing_source_snapshots source
LEFT JOIN concept_selections selection
  ON selection.id = source.selection_id AND selection.project_id = source.project_id
LEFT JOIN concepts concept
  ON concept.id = source.concept_id AND concept.project_id = source.project_id
WHERE source.deleted_at IS NULL
  AND (source.selection_id IS NULL OR source.concept_id IS NULL
       OR selection.id IS NULL OR concept.id IS NULL);
```

0행이면 V21의 `source_type='LEGACY'` default 및 상호 배타 authority constraint와 호환된다.

## 2. Finance 사용자 여정

1. TechOps 준비·Snapshot이 없는 프로젝트에서 current Market FULL과 current BM을 준비한다.
2. BM의 source Market version이 current Market FULL과 정확히 같은지 확인한다.
3. Finance를 열어 TechOps 완료 안내나 TechOps CTA가 없고 Market/BM version과 TAM·SAM·성장률·가격·Concept 가설·BM 재무 전달정보가 보이는지 확인한다.
4. 섹션 “새로고침”이 화면을 닫지 않고 current REST 상태를 다시 읽는지 확인한다.
5. 단일 추천과 “미확정 항목 그룹 추천”을 실행한다. loading/실패/재시도/설명/가정/신뢰도를 확인한다.
6. 추천값이 input에 미리 보이는 상태에서 “재무 입력 저장”을 누르고, ACCEPT 전에는 해당 추천값이 사용자 결정으로 저장되지 않는지 확인한다.
7. ACCEPT, 값 편집 후 EDIT_AND_ACCEPT, REJECT, REQUEST_ALTERNATIVE를 각각 확인한다.
8. 1·2·3년 목표, churn, 신규 고객 수가 typed 결과로 표시되는지 확인한다.
9. Snapshot 확정 후 분석을 실행하고 3개년 손익, BEP, 운전자금, 월별 표·차트, Monte Carlo, P10/P50/P90, 손실·회수 확률, stress, findings/cautions/actions/disclaimer를 확인한다.
10. 다음 단계가 존재하지 않는 `/panel-survey`가 아니라 현재 `/marketing` route로 이동하는지 확인한다.

## 3. Marketing 사용자 여정

1. CPV2 current selection과 exact-match non-stale Market Analysis Seed가 있는 프로젝트에서 Marketing Source가 자동 선택되는지 확인한다.
2. CPV2 seed missing/stale/foreign/mismatch이면 legacy source로 fallback하지 않는지 확인한다.
3. 콘텐츠 유형·채널·목적·톤·길이·CTA·필수/제외 표현·추가 지시를 입력한다.
4. PNG/JPEG 20MB 이하 reference image를 선택하고 upload 결과 artifactId가 create request에 포함되는지 확인한다.
5. reference가 없을 때 image generate, 있을 때 image edit가 호출되는지 provider 관측으로 확인한다.
6. 금지 표현 또는 필수 고지 누락 결과에서 이미지 생성 호출이 0회인지 확인한다.
7. 생성 중 progress, 통합 image+copy canvas, style, editor, legal panel, revision, copy, download, save, regenerate, finalize를 확인한다.
8. 생성 JPEG가 `ai-artifacts/{UUID}.jpg`에 있고 browser에는 presigned URL이 제공되는지 확인한다.

## 4. LIVE 범위

Docker, actual PostgreSQL Flyway, OpenAI, Tavily, MinIO, 실제 브라우저 journey는 이 로컬 작업에서 실행하지 않는다. 위 절차는 사용자가 실제 환경에서 별도로 검증한다.

