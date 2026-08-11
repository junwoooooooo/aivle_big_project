# DB migration 충돌 및 재번호 계획

## 1. 결론

- Target의 현재 Flyway 최대 번호는 **V13**이다.
- Target의 기존 V1~V13은 수정하지 않는다.
- donor의 V10~V14를 파일명 그대로 복사하면 Target V10~V13과 충돌한다.
- 실제 내용과 의존 순서를 기준으로 Market V10부터 Finance V14까지를 Target V14~V19 후보로 재번호한다.
- CPV2 donor migration은 Target에 이미 동일 내용이 존재하므로 새 migration을 만들지 않는다.
- 아래 계획은 Session 1 문서화 결과이며 실제 SQL 생성·적용은 하지 않았다.

## 2. Target 현재 migration 기준선

| Target 파일 | 목적 요약 | 판정 |
|---|---|---|
| `V1__new_pipeline_baseline.sql` | 초기 제품 스키마 기준선 | 정본, 변경 금지 |
| `V2__v2_10e0_contract_corrections.sql` | 계약/제약 보정 | 정본, 변경 금지 |
| `V3__v2_10e1_concept_selection_async.sql` | Concept selection 비동기 실행 | 정본, 변경 금지 |
| `V4__v2_10e2_techops_proposal_async.sql` | TechOps proposal 비동기 실행 | 정본, 변경 금지 |
| `V5__v2_10f_project_evidence_artifacts.sql` | 프로젝트 evidence/artifact | 정본, 변경 금지 |
| `V6__concept_factory_runtime_stabilization.sql` | Concept Factory runtime 안정화 | 정본, 변경 금지 |
| `V7__concept_factory_runtime_completion.sql` | Concept Factory runtime 완성 보강 | 정본, 변경 금지 |
| `V8__concept_factory_cross_service_contract_hardening.sql` | 서비스 간 Concept Factory 계약 hardening | 정본, 변경 금지 |
| `V9__concept_factory_runtime_budget_constraints.sql` | Concept Factory TaskRun budget 제약 | 정본, 변경 금지 |
| `V10__add_concept_portfolio_v2_product.sql` | CPV2 제품 스키마 | 정본, 변경 금지 |
| `V11__harden_concept_portfolio_lineage.sql` | CPV2 lineage hardening | 정본, 변경 금지 |
| `V12__add_concept_portfolio_selection_and_legal_handoff.sql` | CPV2 selection/legal handoff | 정본, 변경 금지 |
| `V13__bind_concept_portfolio_delta_legal_revision.sql` | CPV2 delta/legal revision 결속 | 정본, 변경 금지 |

Target V1~V9는 donor 계열의 공통 기반과 동일하다. Target V10~V13은 `donor-integration-local` V10~V13과 파일명 및 내용이 동일하다.

## 3. CPV2 번호 이동 이력 판정

`donor-market`에는 CPV2 SQL이 V13~V16으로 존재하지만 내용 해시 비교 결과 Target V10~V13과 각각 동일하다.

| donor-market 파일 | 동일한 Target 파일 | 처리 |
|---|---|---|
| `V13__add_concept_portfolio_v2_product.sql` | `V10__add_concept_portfolio_v2_product.sql` | 추가 금지 |
| `V14__harden_concept_portfolio_lineage.sql` | `V11__harden_concept_portfolio_lineage.sql` | 추가 금지 |
| `V15__add_concept_portfolio_selection_and_legal_handoff.sql` | `V12__add_concept_portfolio_selection_and_legal_handoff.sql` | 추가 금지 |
| `V16__bind_concept_portfolio_delta_legal_revision.sql` | `V13__bind_concept_portfolio_delta_legal_revision.sql` | 추가 금지 |

이는 같은 기능이 branch별로 다른 번호를 받은 경우다. donor-market의 번호를 따라 Target CPV2 migration을 이동하거나 다시 실행해서는 안 된다.

## 4. donor migration 충돌 map

| donor | donor filename | 목적 | Target 번호 충돌 | Target에 동일 내용 존재 | 새 번호 필요 |
|---|---|---|---:|---:|---:|
| donor-main / donor-market / donor-aidev / donor-mini | `V10__market_research.sql` | Market research run/version 및 관련 제약 | O: Target V10은 CPV2 core | X | O |
| donor-main / donor-market / donor-mini | `V11__twin_survey.sql` | Twin survey run/version 및 관련 제약 | O: Target V11은 CPV2 runtime | X | O |
| donor-market | `V12__bm_plan_preparation.sql` | 프로젝트 BM plan preparation 저장 | O: Target V12는 CPV2 selection | X | O |
| donor-mini | `V12__task_run_error_reason.sql` | `task_runs.last_error_reason` 추가 | O: Target V12는 CPV2 selection | X | O |
| donor-mini | `V13__finance_business_model_source.sql` | Finance preparation/snapshot의 BM·Market upstream source 연결 및 기존 제약 보정 | O: Target V13은 CPV2 alignment | X | O |
| donor-mini | `V14__financial_snapshot_active_preparation_unique.sql` | active Finance snapshot의 preparation별 부분 unique index | 번호 자체는 비어 있지 않게 될 예정 | X | O |
| donor-integration-local | `V10`~`V13` CPV2 | Target CPV2와 동일 | O | O | X |
| donor-market | `V13`~`V16` CPV2 | 번호만 이동된 Target CPV2와 동일 | O/향후 번호 충돌 | O | X |

## 5. 권장 Target 재번호

| 순서 | 새 Target filename 후보 | 원본 donor filename | 적용 목적 | 선행 의존성 |
|---:|---|---|---|---|
| 1 | `V14__market_research.sql` | `V10__market_research.sql` | Market run/version 스키마 | Target V13 |
| 2 | `V15__twin_survey.sql` | `V11__twin_survey.sql` | Twin run/version 스키마 | Target V13 |
| 3 | `V16__bm_plan_preparation.sql` | `V12__bm_plan_preparation.sql` | BM 4개 planned cell 및 실행 제약 입력 저장 | V14 Market |
| 4 | `V17__task_run_error_reason.sql` | `V12__task_run_error_reason.sql` | donor의 안전 실패 사유를 TaskRun 수준에 보존 | Target TaskRun/TaskAttempt 모델 |
| 5 | `V18__finance_business_model_source.sql` | `V13__finance_business_model_source.sql` | Finance가 Market/BM source를 명시적으로 참조 | V14 Market, V16 BM, 기존 Finance |
| 6 | `V19__financial_snapshot_active_preparation_unique.sql` | `V14__financial_snapshot_active_preparation_unique.sql` | 활성 snapshot 유일성 규칙 | V18 Finance 보정 |

재번호 시에는 파일명만 바꾸는 것이 아니라 각 SQL의 실제 전제 테이블·제약명·인덱스명을 Target V13 스키마와 대조해야 한다. 분석 알고리즘과 사용자 결과를 바꾸지 않는 범위에서 SQL seam만 Target 정본에 맞춘다.

## 6. migration별 주의사항

### V14 Market

- `market_research_runs`와 `market_research_versions`의 run/version 관계를 보존한다.
- project ownership, TaskRun 연결, input hash, source run, partial/missing/evidence/caveat 집계 필드를 확인한다.
- donor가 local run path를 권위로 가정하는 컬럼이 있으면 Target Artifact/upstream snapshot ID로 adapter를 둔다.
- 같은 TaskRun의 version 중복 materialization을 막는 제약을 Target idempotency 규칙과 함께 확인한다.

### V15 Twin

- `twin_survey_runs`와 `twin_survey_versions`의 immutable version 관계를 보존한다.
- Twin Bank 파일 자체나 bank content를 DB migration에 삽입하지 않는다.
- gate/sampling/result JSON이 caveat, MDE, profile, interviews를 손실 없이 담는지 contract test로 검증한다.

### V16 BM plan

- donor의 4개 planned BMC 입력과 budget/months/team execution constraints를 보존한다.
- Market current를 나중에 재조회해 결과가 바뀌지 않도록 실행 시 source Market version을 별도 snapshot으로 결속한다.
- BM은 Persona 테이블이나 과거 Persona FK를 요구하도록 복원하지 않는다.

### V17 TaskRun error reason

- Target에는 TaskAttempt 수준의 정규화된 failure 정보가 있으므로 중복 권위가 되지 않게 한다.
- donor UI/서비스가 요구하는 최신 안전 오류를 TaskRun에 denormalize할 필요가 있는지 구현 전에 확인한다.
- 본 감사에서는 donor failure semantics 보존을 위해 migration 후보를 유지한다. 값의 원천은 TaskAttempt/worker 완료 처리로 단일화해야 한다.

### V18 Finance upstream source

- Market/BM migration 이후에 적용한다.
- donor SQL이 기존 TechOps/Market seed의 `NOT NULL` 또는 FK를 완화하는 부분은 Target 실제 데이터와 제약을 다시 검증한다.
- Finance preparation과 snapshot 모두 실행 당시 source ID를 보존해야 한다.
- current Finance snapshot 권위 및 ProjectModuleStatus를 donor 방식으로 후퇴시키지 않는다.

### V19 active snapshot unique

- donor가 기존 preparation unique constraint를 제거하고 active row만 유일하게 만드는 부분 unique index를 생성한다.
- reopen/history 요구를 위한 변경이므로 Target stale/history 모델과 동시 검증한다.
- 기존 데이터에 active 중복이 있는지 read-only 사전 쿼리 후 적용한다. migration 안에서 임의 삭제·정리하지 않는다.

## 7. AIdev Visual 및 추가 스키마

AIdev donor에는 Visual 전용 DB migration이 없다. 생성 결과를 `ai/outputs` 로컬 파일로만 기록한다. Target에서는 기존 Artifact/MinIO와 프로젝트 소유권을 사용해야 하므로 별도 metadata 테이블이 정말 필요한지는 구현 설계에서 결정한다.

- 기존 artifact 테이블로 충분하면 migration을 추가하지 않는다.
- 별도 visual generation version 테이블이 필요하면 V19 이후의 새 번호를 사용한다.
- donor에 없는 스키마를 Session 1에서 추측해 번호·DDL로 확정하지 않는다.

## 8. 구현 세션 검증 절차

1. Target V13까지의 깨끗한 DB와 대표 기존 데이터 DB 두 종류를 준비한다.
2. donor SQL을 그대로 실행하지 말고 Target schema/constraint 이름과 대조한 재번호 SQL을 검토한다.
3. Flyway validate/migrate를 수행하고 기존 migration checksum이 변하지 않았는지 확인한다.
4. Market → BM → Finance FK 및 삭제 정책을 contract test로 확인한다.
5. 동일 TaskRun 중복 완료, worker retry, Finance reopen에서 unique 제약 동작을 검증한다.
6. downgrade SQL로 기존 migration을 수정하지 않는다. 문제가 있으면 다음 번호의 보정 migration을 사용한다.
