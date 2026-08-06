# Legal Review Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: LegalReviewRun, findings and Korean legal source provenance
- Supersedes: Legacy StructuredPlan-based legal review
- Implementation Status: NOT_STARTED

## Aggregate ownership

LegalReviewRun은 Project 소유 aggregate이며 exact IdeaVersion을 input으로 고정한다. 한 IdeaVersion에 여러 Run을 허용한다. LegalFinding과 LegalSourceReference는 Run composition이고 Project owner scope를 상속한다.

## LegalReviewRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | LegalReviewRun identifier; 정확히 한 Project와 IdeaVersion |
| Cardinality | IdeaVersion `1:N` LegalReviewRun; Project current accepted legal reference 최대 하나 |
| Input | exact IdeaVersion identifier, input snapshot/hash와 legal contract version |
| Task binding | 요청 수락 후 정확히 하나의 TaskRun; retry는 같은 TaskRun의 새 Attempt, rerun은 새 LegalReviewRun/TaskRun |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution display | 연결된 TaskRun 상태 projection; 독립 execution source of truth가 아님 |
| Result status | `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` |
| Source coverage | `AUTHORITATIVE_COMPLETE`, `DEGRADED`, `UNAVAILABLE`; execution status와 분리 |
| Time | 생성, 시작, 완료, 마지막 갱신 시각 |
| Concurrency | terminal transition과 current legal reference 채택에 optimistic concurrency 필요 |
| Provenance | exact IdeaVersion, TaskRun/TaskResult, source registry, AI-generated summary와 source fact 구분 |
| Delete | archive 가능; concept/report가 참조하면 hard delete 금지 방향 |
| Uniqueness | Run identifier 전역 유일; 동일 idempotency/input snapshot 중복 생성은 TaskRun 정책으로 방지 |

`PASS`와 `PASS_WITH_CONDITIONS`만 ConceptGenerationRun의 통과 가능한 legal context다. `PASS_WITH_CONDITIONS`의 조건은 generation input snapshot에 포함한다. 나머지 result status는 correction, 추가 정보 또는 전문가 검토 gate를 요구하며 AI 결과만으로 우회하지 않는다.

TaskRun `SUCCEEDED`만으로 legal result가 확정되지 않는다. Exact input에 대한 `ADOPTED` TaskResult, domain validation과 legal result status가 모두 존재해야 한다. Task failure/timeout/cancellation과 legal result, `CURRENT`/`STALE` validity는 별도 차원이다.

## LegalFinding

| Concern | Logical contract |
|---|---|
| Identifier/owner | finding identifier; LegalReviewRun composition |
| Cardinality | LegalReviewRun `1:N` LegalFinding |
| Semantics | finding type, severity, claim/summary, affected idea element, required action |
| Evidence classification | `CONFIRMED_SOURCE_FACT` 또는 `ASSUMPTION`; AI interpretation은 source fact와 별도 |
| Mutability/version | Run completion 후 immutable; 정정은 새 Run/Finding |
| Lifecycle/validity | active within run, superseded by newer run, current/stale |
| Time | 생성 시각; source observation 시각과 구분 |
| Concurrency | immutable content에는 불필요 |
| Provenance | 하나 이상 source link 또는 assumption rationale; AI proposal origin |
| Delete | parent Run retention을 따름; report provenance에 포함되면 보존 |
| Uniqueness | finding identifier 유일; 같은 Run 안의 중복 finding 판단 규칙은 P2.4 이후 vocabulary/API contract에서 결정 |

Finding type/severity의 상세 vocabulary는 P2.4 이후 계약으로 남기지만, severity가 result status나 legal conclusion을 자동 확정하지 않도록 한다.

## LegalSourceReference

| Concern | Logical contract |
|---|---|
| Identifier/owner | source reference identifier; LegalReviewRun의 source registry에 속함 |
| Cardinality | Run `1:N` source; Finding `N:M` source link |
| Source channel | `MOLEG_API`, `LEGAL_MCP` |
| Semantics | 법령 identifier, 법령명, 조문, 조회 시각, source freshness/currentness, provenance URL 방향 |
| Authority | MOLEG_API는 원문·identifier·현재성 확인의 authoritative channel; LEGAL_MCP는 discovery channel |
| Degraded | source별 성공/실패와 누락 사유를 기록; 한 채널 실패를 다른 채널 성공으로 숨기지 않음 |
| Mutability/version | 조회 observation은 immutable; 재조회는 새 reference 또는 새 Run |
| Time | 조회 시각과 record 생성 시각 |
| Concurrency | immutable observation에는 불필요 |
| Security | URL은 legal provenance일 수 있으나 Storage URL이 아니며 credential/secret을 저장하지 않음 |
| Delete | Run/finding/report provenance retention을 따름 |
| Uniqueness | Run 안에서 channel + legal identifier + article + observation identity의 논리 중복 방지; exact physical key는 후속 Phase |

AI Server가 법령 MCP와 법제처 API를 조정하지만 RDB/Object Storage에 접근하지 않는다. Secret은 AI Server 환경변수로만 주입하고 결과나 source reference에 저장하지 않는다.

## Correction and stale rules

- Legal result는 input IdeaVersion을 직접 수정하지 않는다.
- `REVISION_REQUIRED` 또는 사용자 correction은 source finding을 참조한 새 IdeaVersion을 만들고 새 LegalReviewRun을 시작한다.
- 이전 IdeaVersion/Run/Finding/source chain은 immutable history로 유지하고 새 chain의 current reference가 되지 않는다.
- 새 current IdeaVersion은 이전 legal run과 모든 concept/downstream을 `STALE`로 만든다.
- 같은 IdeaVersion의 새 accepted LegalReviewRun이 current가 되면 이전 legal context 기반 concept chain은 자동 재활성화하지 않고 stale 재평가가 필요하다.
- `EXPERT_REVIEW_REQUIRED`는 전문 자문 필요 표시이며 서비스가 법률 자문 또는 법적 결론을 제공한다는 뜻이 아니다.
