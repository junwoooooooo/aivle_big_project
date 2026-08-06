# EXTERNAL MODULE HANDOFF CONTRACT v1.0

## 1. 목적

시장분석, BM·재무분석, 인터뷰지·페르소나 응답 모듈을 저장소 내부 Entity와 분리한다.

## 2. 공통 Handoff Envelope

```json
{
  "contract": "module-handoff-v1",
  "handoffId": "uuid",
  "projectId": 1,
  "module": "MARKET_ANALYSIS",
  "inputSnapshotId": "uuid",
  "inputSnapshotHash": "sha256:...",
  "requestedAt": "ISO-8601",
  "callback": {"mode": "POLL_OR_CALLBACK", "reference": "..."}
}
```

## 3. 시장분석 입력

SelectedConceptSnapshot과 Concept Legal Assessment를 포함한다. Entity ID만 넘기지 않고 불변 Snapshot 본문과 Hash를 전달한다.

## 4. 시장분석 결과

- moduleRunId
- inputSnapshotId
- status
- resultReference
- summary
- competitors
- planningChangeProposals
- completedAt
- resultHash

## 5. Finalized Planning 입력

BM·재무와 Persona 응답 모듈은 동일 FinalizedPlanningSnapshot을 소비한다.

## 6. 상태

ACCEPTED, QUEUED, RUNNING, NEEDS_INPUT, COMPLETED, FAILED, CANCELLED

## 7. Event

module.accepted, queued, started, progress, completed, failed

progress Event는 퍼센트 없이 stageKey와 safeMessageKey를 사용한다.

## 8. Idempotency

`module + inputSnapshotHash + requestedOperation`을 Idempotency Key로 사용한다.

## 9. Callback 안전

외부 Callback은 서명 또는 내부 인증, timestamp, replay 방지, project·handoff 일치 검증을 요구한다.

## 10. 미연결 모드

외부 모듈이 없을 때 Module 상태는 NOT_CONNECTED다. Stub Adapter로 UI·계약을 검증할 수 있으나 사용자에게 실제 분석 완료로 보이지 않는다.
