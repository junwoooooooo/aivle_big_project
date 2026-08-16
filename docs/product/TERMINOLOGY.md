# Canonical Terminology

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Product language and prohibited legacy claims
- Supersedes: Terminology embedded in legacy product documents
- Implementation Status: NOT_STARTED

Project는 하나의 아이디어 검증 과정 전체다. IdeaSource logical type은 TEXT 또는 FILE이며 질문 응답 UI는 TEXT source capture 방식이다. ConceptCandidate는 생성된 후보 identity이고 ConceptVersion은 그 후보의 immutable 내용이다. Persona Interview는 하나의 exact PersonaCardVersion을 기준으로 독립 수행한다. Marketing A/B Comparison은 실제 사용자 실험이 아닌 exact asset versions의 상대 비교다. Final Report는 저장·version 조회·HTML view·PDF export 가능한 immutable snapshot history다. TaskRun은 Spring이 관리하는 업무 요청, TaskAttempt는 개별 실행, TaskResult는 수신·검증·채택 evidence다.

`IdeaInterpretationRun`은 exact current IdeaSourceExtraction을 정규화하는 AI proposal 실행이며 confirmed IdeaVersion이 아니다. `USER_AUTHORED` IdeaVersion은 Interpretation 없이 사용자가 작성·확정하고, `AI_ASSISTED` IdeaVersion은 adopted Interpretation result를 검토·수정한 authenticated confirmation snapshot이다.

`PersonaCardGenerationRun`은 exact PersonaStudy/ConceptSelection/ConceptVersion에서 synthetic Persona Card identity와 최초 version을 생성하는 AI-backed Domain Run이다. `PersonaCard`는 logical identity, `PersonaCardVersion`은 immutable Three-Layer content이며 PersonaInterview는 exact PersonaCardVersion을 입력으로 사용한다.

`Domain Run`은 exact business input과 adopted business result/provenance를 묶는 업무 실행 record다. AI-backed Domain Run은 수락 시 TaskRun과 1:1로 결합하지만 execution lifecycle의 source of truth는 TaskRun이다. `Attempt`는 같은 TaskRun 안의 retry 가능한 개별 시도, `TaskResult`는 실행 응답과 validation/adoption evidence, `TaskArtifact`는 선택적인 Spring-owned artifact reference다.

`Retry`는 같은 Domain Run/TaskRun에 새 TaskAttempt를 추가하는 실행 복구다. `Rerun`은 사용자의 명시적 새 업무 요청으로 새 Domain Run과 새 TaskRun을 만든다. 두 행위를 같은 history mutation으로 취급하지 않는다.

`Version`은 immutable 업무 내용, `Decision`/`Selection`은 사용자 선택, `Stage`는 현재 Workflow 표시, `Capability`는 Spring이 조건으로 계산한 실제 실행 가능 여부다. `CURRENT`/`STALE`은 execution success/failure와 별개인 domain validity다. Capability cache는 구현 최적화이며 canonical 업무 상태가 아니다.

Target 문서에서 StructuredPlan, 12개 고정 section, FILLED/WAIVED, fixed cluster persona, market response prediction, purchase probability, runtime report를 신규 기능 명칭으로 사용하지 않는다.

Public API에서 `ResourceReference`는 opaque `type`/`id`로 exact resource를 가리키고, `currentReferences`는 Spring이 검증한 현재 non-stale pointer 집합이다. `Idempotency-Key` replay는 같은 command의 재전송이며, Domain `Rerun`은 새 Domain Run/TaskRun을 만드는 별도 command다. `TaskRunPublicView`는 execution projection이고 TaskAttempt/provider 내부 정보가 아니다.
