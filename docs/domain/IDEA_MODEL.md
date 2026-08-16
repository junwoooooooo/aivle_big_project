# Idea Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: IdeaSource, extraction, AI interpretation and immutable user-confirmed IdeaVersion logical schema
- Supersedes: StructuredPlan input model
- Implementation Status: NOT_STARTED

## Aggregate ownership

`Project` owns IdeaSource, IdeaInterpretationRun and IdeaVersion history. 모두 Project owner scope 대상이다. IdeaSource/Extraction은 입력 evidence, InterpretationRun은 AI proposal, IdeaVersion은 사용자가 확정한 immutable 업무 내용이며 문서가 aggregate root가 되지 않는다.

## IdeaSource

| Concern | Logical contract |
|---|---|
| Identifier | 안정적인 IdeaSource identifier |
| Owner | Project composition; 정확히 한 Project |
| Source type | `TEXT` 또는 `FILE`. 질문 응답 UI는 응답 묶음을 `TEXT` source로 capture하며 별도 domain source type을 만들지 않음 |
| FILE allowlist | 초기 `DOCX`, plain text. PDF/XLSX/PPTX 제외; allowlist는 확장 가능 |
| Semantics | 원본 사용자 입력, capture channel, 표시 이름, FILE metadata/StoredFile reference 방향 |
| Mutability | 원본 content와 FILE reference는 immutable; validation/extraction/archive lifecycle만 mutable |
| Version | source 수정은 새 IdeaSource 생성. parser 변경·재실행은 새 IdeaSourceExtraction version 생성 |
| Time | 접수/생성 시각, lifecycle 마지막 갱신 시각 |
| Concurrency | lifecycle/current-extraction 갱신에 optimistic concurrency 필요 |
| Lifecycle | `RECEIVED`, `VALIDATED`, `EXTRACTED`, `REJECTED`, `QUARANTINED`, `ARCHIVED` |
| Provenance | USER 입력 actor, capture channel, content checksum; AI proposal로 분류하지 않음 |
| Delete | archive/reference 해제 우선. FILE bytes 삭제는 Spring retention과 RDB reference 확인 후 수행 |
| Uniqueness | source identifier 전역 유일; FILE Storage reference는 owner·artifact lifecycle 규칙과 일치 |

FILE metadata와 bytes는 Spring이 소유한다. AI Server에는 filename, object key, Storage URL, presigned URL 또는 FILE bytes를 전달하지 않고 검증된 extracted content만 전달한다.

## IdeaSourceExtraction

IdeaSourceExtraction은 source 원본과 AI/domain 입력 사이의 immutable 변환 결과다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | extraction identifier; 정확히 한 IdeaSource 소유, 동일 Project scope 상속 |
| Cardinality | IdeaSource `1:N` extraction history; current successful extraction 최대 하나 |
| Input reference | exact IdeaSource identifier와 원본 checksum |
| Semantics | extracted content, extractor/parser contract version, extraction warnings, content checksum, language/encoding 방향 |
| Mutability/version | immutable, source-local extraction version number 유일 |
| Time | 시작/완료 또는 생성 시각; 실패 evidence 시각 포함 |
| Concurrency | content update 없음; current extraction pointer 변경만 optimistic concurrency |
| Lifecycle | `SUCCEEDED`, `FAILED`; current validity는 `CURRENT` 또는 `STALE` |
| Provenance | parser/extractor identity와 version; AI 생성 content가 아님 |
| Delete | source와 함께 retention 관리; IdeaVersion이 참조 중이면 삭제 금지 방향 |

TEXT source도 canonical content/checksum을 고정하기 위해 direct extraction record를 가질 수 있다. FILE source는 Spring parser가 DOCX/plain text를 추출한다. 상세 extraction block schema와 size limit은 후속 contract에서 정한다.

## IdeaInterpretationRun

IdeaInterpretationRun은 하나 이상의 exact current IdeaSourceExtraction을 해석·정규화하는 AI-backed Domain Run이며 confirmed IdeaVersion이나 사용자 결정이 아니다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | Run identifier; 정확히 한 Project와 owner scope |
| Input | 같은 Project의 exact `CURRENT` IdeaSourceExtraction 하나 이상, source/extraction snapshot과 canonical hash, normalization contract version |
| Task binding | 요청 수락 후 정확히 하나의 TaskRun; TaskRun이 execution lifecycle source of truth |
| Result adoption | exact TaskRun의 validated/adopted TaskResult 최대 하나와 domain validation 필요; TaskRun `SUCCEEDED`만으로 채택 금지 |
| Result direction | original source summary, normalizedDescription, structured facts/assumptions/constraints/openQuestions, readiness, warnings, evidenceNeeds와 provenance |
| Boundary | AI proposal이며 user confirmation/IdeaVersion을 자동 생성하지 않음; 불명확한 항목을 fact로 승격하지 않음 |
| Readiness | `UNDER_SPECIFIED`는 필요한 정보/open question, `APPROPRIATE`는 정규화 적정 수준, `OVER_SPECIFIED`는 사용자 제약을 보존하며 과도한 세부를 정리 |
| Constraint rule | 어떤 readiness에서도 사용자 제약을 임의 삭제하지 않고 fact/assumption을 분리 |
| Mutability/validity | input immutable; adopted result reference와 `CURRENT`/`STALE`만 controlled update |
| Retry/rerun | retry는 같은 TaskRun의 새 TaskAttempt; user rerun은 새 IdeaInterpretationRun과 새 TaskRun |
| Stale | source/user input 또는 current extraction 변경 시 이전 input과 비교해 `STALE` 가능 |
| Provenance | source/extraction refs, snapshot/hash, TaskRun/TaskResult, contract version |
| Uniqueness | Run identifier/idempotency scope; input extraction id 중복 금지 |

Project는 `1:N` IdeaInterpretationRun을 가진다. Run은 extraction과 logical `N:M`, TaskRun과 `1:1`, adopted TaskResult와 `1:0..1`, 이를 근거로 확정된 IdeaVersion과 `1:N` reference 관계다.

## IdeaVersion

IdeaVersion은 Project별 immutable idea snapshot이다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | IdeaVersion identifier; Project composition과 owner scope |
| Cardinality | Project `1:N`; version number는 Project 안에서 유일하고 단조 증가 |
| Required semantics | 원본 사용자 입력 표현, normalized description, facts, assumptions, constraints, open questions/research needs |
| Readiness | `UNDER_SPECIFIED`, `APPROPRIATE`, `OVER_SPECIFIED` |
| Source references | 하나 이상 exact IdeaSource와 IdeaSourceExtraction reference. source/extraction과 version은 논리 N:M |
| Creation mode | `USER_AUTHORED` 또는 `AI_ASSISTED`; Spring이 mode에서 `createdBy=USER` 또는 `AI_ASSISTED`를 결정하며 client actor/createdBy를 받지 않음 |
| Interpretation reference | USER_AUTHORED는 없음; AI_ASSISTED는 exact current IdeaInterpretationRun과 adopted TaskResult를 필수 참조. Run `1:N` Version, Version은 source Run 최대 하나 |
| Confirmation | 인증된 사용자의 accepted confirmation command와 optional edit rationale; client `confirmedByUser` boolean을 받지 않음 |
| AI/user boundary | AI result는 proposal이며 final fields는 사용자가 그대로 또는 수정해 확정. Proposal 대비 변경과 actor/time을 provenance에 보존 |
| Mutability | immutable. 수정·correction은 새 version |
| Lifecycle/validity | `DRAFT`, `CONFIRMED`, `SUPERSEDED`; `CURRENT` 또는 `STALE` validity |
| Time | 생성 시각, 사용자 확정 시각 방향 |
| Concurrency | immutable content에는 불필요; Project current IdeaVersion pointer 변경에는 필수 |
| Input snapshot | source/extraction identifiers/checksums, optional InterpretationRun/adopted-result와 confirmation provenance |
| Delete | history/provenance 보존; archive 가능, downstream 참조 중 hard delete 금지 |
| Uniqueness | Project + version number; current confirmed IdeaVersion 최대 하나 |

## Current reference and stale rules

- Project current IdeaVersion은 같은 Project의 `CONFIRMED` version만 가리킨다.
- 새 current IdeaVersion 설정은 이전 IdeaVersion 자체를 삭제하지 않는다.
- 이전 IdeaVersion을 exact input으로 사용한 LegalReviewRun, ConceptGenerationRun과 모든 transitive downstream은 `STALE`이다.
- Legal correction은 기존 IdeaVersion을 직접 수정하지 않고 correction provenance를 포함한 새 IdeaVersion과 새 legal chain을 만든다.
