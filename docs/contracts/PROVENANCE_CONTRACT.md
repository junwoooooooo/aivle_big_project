# Provenance Contract Direction

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Source, AI result and user decision traceability
- Supersedes: Legacy snapshot and hash contracts
- Implementation Status: NOT_STARTED

IdeaSource/Extraction, IdeaInterpretationRun의 AI proposal, 사용자 IdeaVersion confirmation, 외부 법령 근거, Concept/분석/Persona/Marketing 실행, AI 권고, 사용자 선택과 FinalReportVersion의 연결을 추적해야 한다. versioned 결과는 생성 당시 input snapshot/hash와 TaskRun/TaskAttempt를 식별할 수 있어야 한다. AI 제안, 사용자 결정, 외부 출처 사실과 가정을 서로 다른 provenance category로 구분한다.

Quick와 Detailed는 shared core input provenance를 참조하되 각 analysis-specific 입력·결과를 구분한다. 법률 provenance는 조회 시각, 법령 식별자, 조문과 source channel을 보존하고 한쪽 출처 실패 시 degraded 상태와 누락 출처를 표시한다. provenance는 audit와 다르지만 correlation할 수 있어야 한다.

## Logical provenance reference

| Category | Required direction |
|---|---|
| USER_INPUT | actor, IdeaSource/extraction과 생성 시각 |
| EXTERNAL_SOURCE_FACT | source channel, observation identity/time, citation/currentness |
| ASSUMPTION | assumption text/identity, origin과 검증 필요성 |
| AI_PROPOSAL | TaskRun/Attempt/Result, input snapshot/hash와 contract version |
| USER_DECISION | actor, decision/selection identifier, timestamp, rationale와 reviewed evidence refs |
| DETERMINISTIC_CALCULATION | input snapshot, calculation rule/version과 result; AI explanation과 분리 |

모든 Run은 exact input Version/Decision/reference와 snapshot/hash를 가진다. immutable Version은 source set을 고정하고 current pointer를 동적으로 따라가지 않는다. FinalReportVersion은 포함한 upstream reference 집합과 위 category를 snapshot 안에서 구분한다.

Internal result는 `category`, `statementKey`, request-local `sourceKeys`, external source references, 생성 시각, optional confidence/uncertainty, verification 필요 여부와 optional caveat를 분리한다. AI Server는 `USER_DECISION`을 새로 생성하지 않고 요청에 이미 포함된 결정을 echo/reference할 수만 있다. Request-local key는 1–64자 bounded syntax와 `INPUT`/`OUTPUT_PROPOSAL` namespace를 사용하고 Spring이 Domain reference로 매핑한다. 외부 authoritative identifier는 별도 schema이며 AI가 입력에 없던 source identity를 임의 생성하지 않는다.

상세 public 표현은 [Public API v2 Contract](PUBLIC_API_V2_CONTRACT.md), internal 표현과 canonical input hash는 [Internal Spring–AI API v1 Contract](INTERNAL_AI_API_V1_CONTRACT.md)를 따른다. Fixture와 hash/재현성 검증은 P2.6에서 결정한다. Retention 기간은 후속 operations/migration Phase에서 확정한다.
