# 04. 데이터 모델 — 테이블 57개

- 정본: `backend/src/main/resources/db/migration/` (**V1–V21, 21개 파일**)
- `ddl-auto=validate` — 스키마는 마이그레이션이 유일한 소유자다
- **다음 빈 버전은 V22.** V1–V21은 immutable
- ERD **그림은 아직 없다** → `99_MISSING_MATERIALS.md` **M-05**

> ⚠ `AS_BUILT §6`는 "V1–V36 + Java 마이그레이션 V5·V10"이라고 적고,
> `CLAUDE.md`는 "다음 빈 버전은 V17"이라고 적는다. **둘 다 틀리다.**
> 실제 파일은 21개이고 `backend/src/main/java/db/migration/` 디렉터리는 존재하지 않는다.

---

## 1. 도메인 8군 — 테이블 57개 전수

### ① 사용자 · 인증 (4)
`users` · `refresh_tokens` · `audit_events` · `admin_action_tokens`

### ② 프로젝트 · 파일 (4)
`projects` · `stored_files` · `project_evidence_artifacts` · `service_settings`

### ③ 아이디어 (5)
`idea_briefs` · `idea_brief_fields` · `idea_brief_attachments` · `idea_questions` · `idea_answers`

### ④ 컨셉 — 가장 큰 군 (17)
**옛 컨셉 팩토리 (9)**
`concepts` · `concept_slots` · `concept_attempts` · `concept_factory_runs` ·
`concept_selections` · `concept_input_requests` · `concept_input_responses` ·
`concept_rejection_summaries` · `concept_hypothesis_decisions`

**컨셉 포트폴리오 v2 (6)** — V13–V16
`concept_portfolio_runs` · `concept_portfolio_concepts` · `concept_portfolio_continuations` ·
`concept_portfolio_selections` · `concept_portfolio_hypothesis_decisions` ·
`concept_portfolio_delta_legal_reviews`

**컨셉 법률 (2)**
`concept_legal_assessments` · `concept_legal_evidence_links`

### ⑤ 법률 (3)
`concept_legal_regulatory_reports` · `legal_context_packs` · `legal_evidence`

### ⑥ 시장 · BM (5)
`market_research_runs` · `market_research_versions` · `market_analysis_seed_snapshots` ·
`bm_plan_preparations` · `research_competitor_seeds`

### ⑦ 기술·운영 (3) / 재무 (3) / 마케팅 (4) / 패널조사 (2)
- 기술운영: `tech_ops_input_preparations` · `tech_ops_input_snapshots` · `tech_ops_evidence_references`
- 재무: `financial_input_preparations` · `financial_input_snapshots` · `financial_analysis_reports`
- 마케팅: `pipeline_marketing_contents` · `pipeline_marketing_content_revisions` ·
  `pipeline_marketing_assets` · `marketing_source_snapshots`
- 패널조사: `twin_survey_runs` · `twin_survey_versions`

### ⑧ 실행 인프라 (7)
`task_runs` · `task_attempts` · `task_results` · `job_events` ·
`module_runs` · `module_results` · `module_handoffs`

---

## 2. 마이그레이션 연혁 — 제품이 자란 순서

| 버전 | 내용 | 무엇이 생겼나 |
|---|---|---|
| **V1** | `new_pipeline_baseline` | 전체 기준선 (Integration-Local 병합으로 교체됨) |
| V2 | 계약 정정 | |
| V3 | 컨셉 선택 비동기화 | |
| V4 | 기술·운영 제안 비동기화 | |
| V5 | 프로젝트 근거 아티팩트 | 근거 저장의 시작 |
| V6–V9 | 컨셉 팩토리 안정화·완료·계약 강화·예산 제약 | 라운드 루프와 후보 상한 |
| **V10** | `market_research` | **시장조사 모듈** |
| **V11** | `twin_survey` | **패널 트윈 조사** |
| V12 | TaskRun 오류 사유 | 실패를 값으로 남기기 |
| **V13–V16** | 컨셉 포트폴리오 v2 (제품·계보·선택·델타 법률) | 컨셉 모듈 교체 |
| V17–V19 | 재무 (BM 원천·스냅샷 유일성·분석 리포트) | |
| **V20** | `bm_plan_preparation` | BM 계획 칸의 사용자 입력 |
| **V21** | `research_competitor_seeds` | 경쟁 씨앗 |

> 발표에 쓸 서사: **V10 → V11 → V13–V16 → V20–V21** 이 순서가 곧
> "시장조사를 붙이고 → 패널을 붙이고 → 컨셉을 갈아끼우고 → 사용자 입력을 받기 시작했다"는
> 제품의 성장 곡선이다.

> ⚠ **V13–V16은 팀원 브랜치에서 V10–V13이던 것을 번호만 뒤로 민 것**이다
> (우리 V10·V11과 정면 충돌했다). 옛 flyway 이력이 남은 DB 볼륨을 붙이면 backend가 기동을
> 거부한다 — `docker compose down -v`로 초기화해야 한다. 시연 전 반드시 확인할 것.

---

## 3. 설계 포인트 (슬라이드용)

### 3-1. 실행 3층이 별도 테이블이다
`task_runs` / `task_attempts` / `task_results` 가 나뉘어 있는 것 자체가 설계 주장이다.
"요청"과 "실행 시도"와 "검증된 결과"는 수명이 다르다 — 한 번의 요청에 여러 시도가 붙고,
그중 채택되는 결과는 **정확히 하나**다.

### 3-2. 모듈마다 `runs` + `versions` 쌍이 있다
`market_research_runs` / `market_research_versions`,
`twin_survey_runs` / `twin_survey_versions`.
**실행(run)과 결과 버전(version)을 분리**해 두면 재실행해도 이전 결과가 남고,
상류가 바뀌었을 때 하류를 `STALE`로 표시할 수 있다.

### 3-3. `*_snapshots` / `*_preparations` 계열
`financial_input_snapshots` · `tech_ops_input_snapshots` · `market_analysis_seed_snapshots` ·
`marketing_source_snapshots` · `bm_plan_preparations` · `*_input_preparations`.

**"AI가 무엇을 보고 그 답을 냈는가"를 붙잡아 두는 테이블들**이다.
재현성과 provenance가 스키마 수준에서 설계돼 있다는 증거다.

### 3-4. 근거(evidence) 테이블이 4개
`project_evidence_artifacts` · `concept_legal_evidence_links` · `legal_evidence` ·
`tech_ops_evidence_references`.
"출처 없는 값 0건" 원칙이 DB 구조에 반영돼 있다.

---

## 4. ERD 작도 지시서 (M-05)

작도할 사람이 이 지시대로 그리면 된다. 소재는 `docs/domain/DOMAIN_OVERVIEW.md`의
"Logical entity ownership matrix"와 "Cardinality matrix"에 있다.

### 4-1. 배치
- **중심에 `projects`** 를 두고 방사형으로 도메인 군을 배치
- **우측에 실행 인프라 군**(`task_runs`·`task_attempts`·`task_results`)을 따로 묶는다
  — 이건 도메인이 아니라 플랫폼이라 성격이 다르다

### 4-2. 색 (도메인별)
| 군 | 색 |
|---|---|
| 사용자·인증 | 회색 |
| 프로젝트·파일 | 남색 (중심) |
| 아이디어 | 하늘 |
| 컨셉 (17개, 최대 군) | 파랑 |
| 법률 | 보라 |
| 시장·BM | 청록 |
| 기술운영·재무·마케팅·패널 | 초록 계열 4단계 |
| 실행 인프라 | **주황** (유일하게 다른 계열 — 플랫폼임을 표시) |

### 4-3. 표시할 것 / 생략할 것
- **표시**: PK/FK와 관계선, 각 군의 테이블 개수 배지
- **생략**: 컬럼 전부 나열. PIILOT p30이 실제로 **글씨가 안 보여 판독 불가**했다.
  우리는 **테이블명 + 핵심 컬럼 3개 이내**로 줄인다
- 1920px 폭에서 테이블명이 읽히는지 반드시 확인

### 4-4. 별첨 2장 구성 권장
1. **개요도** — 8개 도메인 군을 박스로만. 테이블 개수와 관계 방향만 표시 (발표 중 보여줄 장)
2. **상세도** — 전체 57개 (질문 나올 때 여는 장)
