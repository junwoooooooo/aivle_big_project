# EXTERNAL MODULE HANDOFF CONTRACT — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다.

## 1. 목적

Market Analysis, BM, TechOps, Finance와 기존 Persona 외부 기능을 저장소 내부 Entity에서 분리한다. 외부 모듈은 불변 input Snapshot만 소비하고 내부 table을 직접 읽지 않는다.

## 2. 공통 Handoff Envelope

```json
{
  "contract": "module-handoff-v2",
  "handoffId": "uuid",
  "projectId": 1,
  "module": "MARKET_ANALYSIS",
  "inputSnapshotType": "MARKET_ANALYSIS_SEED",
  "inputSnapshotId": "uuid",
  "inputSnapshotHash": "sha256:...",
  "inputSchemaVersion": "2.0",
  "requestedOperation": "ANALYZE",
  "requestedAt": "ISO-8601",
  "callback": {"mode": "POLL_OR_CALLBACK", "reference": "..."}
}
```

Entity ID만 전달하지 않고 검증 가능한 Snapshot 본문 또는 immutable reference, ID, hash, schemaVersion을 전달한다.

## 3. 모듈별 정식 입력

| module | input snapshot | 계약 |
|---|---|---|
| `MARKET_ANALYSIS` | `MarketAnalysisSeedSnapshot` | 유일한 정식 Market 입력 |
| `BUSINESS_MODEL` | BM 실행 직전 확정된 input snapshot | 상세 필수값은 외부 계약 확정 시 Preparation Gate에서 수집 |
| `TECH_OPS` | `TechOpsInputSnapshot` | 내부 Entity 직접 접근 금지 |
| `FINANCIAL_ANALYSIS` | `FinancialInputSnapshot` | TechOps 승계 provenance 포함 |
| `PERSONA_RESPONSE` | 기존 승인된 Persona input snapshot | 이번 V2에서 임의 삭제·변경하지 않음 |

`FinalizedPlanningSnapshot`은 BM, Finance, Persona, Marketing의 공통 mandatory input이 아니다.

## 4. MarketAnalysisSeedSnapshot payload

- 원본 Seed와 optional LOCKED values
- confirmed AI Interpretation
- Selected Concept identity, solution, operation, Legal Fact Pattern
- final accepted hypotheses
- Legal status, controls, required partners/qualifications, prohibited variants, disclosures, official Evidence references
- `snapshotId`, `schemaVersion`, `hash`, `createdAt`

외부 Market module은 이 입력을 분석하지만 Concept나 planning을 변경하지 않는다.

## 5. Market Result

최소 결과:

- `moduleRunId`
- `inputSnapshotId`
- `status`
- `resultReference`
- `summary`
- `competitorProducts`
- `marketSizing`
- `findings`
- `completedAt`
- `resultHash`

`planningChangeProposals`는 결과 계약에 포함하지 않는다. 결과가 선택 Concept, hypothesis decision, Marketing Source를 자동 수정하지 않는다.

## 6. TechOps와 Finance 입력

`TechOpsInputSnapshot`은 준비된 필수 사용자 사실, 채택된 AI 제안, 상위 Snapshot에서 승계한 값의 provenance, optional Evidence reference를 포함한다. AI 생성값을 견적서·BOM·공급사 자료 같은 Evidence로 표현하지 않는다.

`FinancialInputSnapshot`은 고정운영비, 초기투자, 3개년 목표 metric, CAC 구성값, 필요한 조건부 단위원가와 각 값의 source/decision/provenance를 포함한다. TechOps에 있는 값은 다시 요구하지 않는다.

## 7. Marketing boundary

Marketing의 필수 입력은 `MarketingSourceSnapshot`이며 Selected Concept, final accepted hypotheses, Legal Result와 legal guard를 포함한다. Market Result는 mandatory input이 아니다.

## 8. 상태와 Event

외부 run status는 `ACCEPTED`, `QUEUED`, `RUNNING`, `NEEDS_INPUT`, `COMPLETED`, `FAILED`, `CANCELLED`다.

Event type은 `module.accepted`, `module.queued`, `module.started`, `module.progress`, `module.completed`, `module.failed`다. progress는 가짜 퍼센트 대신 `stageKey`와 `safeMessageKey`를 사용한다. Event는 신호이고 module query가 화면 정본이다.

## 9. Idempotency와 stale

`module + inputSnapshotHash + requestedOperation`을 idempotency identity로 사용한다. 결과는 입력 Snapshot ID/hash를 저장한다. 현재 정본과 hash가 다르면 `STALE`로 표시하되 과거 결과를 수정하거나 삭제하지 않는다.

## 10. Callback 안전

Callback은 서명 또는 내부 인증, timestamp, replay 방지, project/handoff/module/input hash 일치 검증을 요구한다. Prompt, provider raw body, secret, stack trace를 callback Event나 사용자 응답에 포함하지 않는다.

## 11. 미연결 모드

외부 모듈이 없으면 `NOT_CONNECTED`다. Stub Adapter는 계약과 UI 검증에 사용할 수 있으나 실제 분석 완료로 표시하지 않는다.
