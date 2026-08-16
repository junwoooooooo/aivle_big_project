# V2-1 — Market Seed 입력 및 AI 해석 결과

## 상태

구현 완료. 실제 Provider·PostgreSQL·브라우저 런타임 승인은 대기 중이다.

## 실행 기준

- 브랜치: `rebuild/new-pipeline-v1`
- 시작 HEAD: `fbb6144`
- 선행 변경: 작업 트리에 존재하는 V2-0 상위 계약 문서 변경을 보존함
- 실행 범위: V2-1만 수행

## 변경 파일

### AI

- `ai/app/tasks/idea_brief/models.py`
- `ai/app/tasks/idea_brief/service.py`
- `ai/app/tasks/idea_brief/mapper.py`
- `ai/tests/test_idea_brief_schema.py`

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/idea/api/IdeaBriefApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/api/IdeaBriefController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefDerivationCommitService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefReadinessCalculator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBrief.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefField.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefFieldCatalog.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefStatus.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaDecisionState.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaFieldProvenance.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/worker/IdeaBriefDerivationWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefCanonicalizationIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefDerivationCommitServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefFieldCatalogTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefFieldInvariantTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefReadinessTests.java`

### Frontend

- `frontEnd/src/features/idea-intake/api/ideaBriefApi.js`
- `frontEnd/src/features/idea-intake/components/IdeaBriefReview.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaBriefReview.test.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaIntakeForm.jsx`
- `frontEnd/src/features/idea-intake/components/MissingRequiredFieldsForm.jsx`
- `frontEnd/src/features/idea-intake/components/MissingRequiredFieldsForm.test.jsx`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.js`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.test.jsx`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.js`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.test.js`
- `frontEnd/src/features/idea-intake/model/ideaQuestions.js`
- `frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx`
- `frontEnd/src/features/idea-intake/styles/idea-intake.css`
- `frontEnd/src/shared/async-events/jobEventMessages.js`

### 단계 문서

- `docs/rebuild/progress/V2-1-MARKET-SEED-INTAKE-AND-INTERPRETATION_RESULT.md`
- `docs/rebuild/verification/V2-1-MARKET-SEED-INTAKE-AND-INTERPRETATION_USER_VERIFICATION.md`

## 구현한 계약

### 최소 Market Seed

- API와 Domain 카탈로그의 필수값을 `ideaOverview`, `problem`, `targetUsers` 정확히 세 개로 변경했다.
- `targetRegion`, `knownCompetitors`, `revenueModel`, `price`, `channels`, `differentiators`를 선택 입력으로 변경했다.
- `budgetConstraint`, `teamConstraint`, `timelineConstraint`, `otherConstraint`를 서로 섞이지 않는 typed field로 분리했다.
- 입력된 선택값은 `USER_INPUT + LOCKED`, 입력하지 않은 값은 `OPEN`으로 유지한다.
- 사용자 field patch가 임의의 decision state를 보내도 실제 값이 있으면 Backend가 `LOCKED`를 강제한다.

### 구 초기 필수 계약 제거

- Idea active 카탈로그와 frontend active feature에서 `targetCustomers`, `beneficiaries`, `expectedOutcome`, `physicalActivity`, `personalData`, `payment`, `requiredPartners`, 구 condition field를 제거했다.
- 법률·결제·개인정보·파트너 세부를 초기 후속 질문으로 생성할 수 없도록 AI Question schema를 필수 세 field로 제한했다.
- 후속 질문은 핵심 Seed의 모호성·모순에만 사용할 수 있다.

### Safety Gate

- AI 응답에 `ALLOW`, `ALLOW_WITH_RESTRICTIONS`, `BLOCK_OR_REFRAME` Safety 계약을 추가했다.
- 최소 안전 범주를 strict enum으로 제한했다.
- Safety와 Legal Review를 Prompt와 Domain 상태에서 구분했다.
- `BLOCK_OR_REFRAME`이면 질문을 제거하고 `SAFETY_BLOCKED`로 종료해 Concept 진행 상태가 되지 않도록 했다.
- 사용자에게는 안전한 사유와 제한만 반환하며 Prompt, 내부 policy, raw reasoning을 반환하지 않는다.

### AI Interpretation

- `interpretedProblem`, `interpretedTargetUsers`, `usageContext`, `industryCategory`, `researchScope`, `conciseIdeaDefinition`을 필수 해석값으로 추가했다.
- 지역·경쟁자 해석을 선택값으로 추가했다.
- Interpretation을 Seed field와 분리된 JSON 경계로 저장하고 API에서 `source=AI_DERIVED`, `authority=REVIEWABLE`로 반환한다.
- `PATCH /api/v3/projects/{projectId}/idea-brief/interpretation`으로 사용자 수정을 지원한다.
- `POST /api/v3/projects/{projectId}/idea-brief/confirm-interpretation`을 추가하고 기존 confirm API는 호환 경로로 유지한다.
- 확정 Snapshot hash에 Safety와 Interpretation을 포함했다.

### Frontend

- 첫 화면에 필수 세 field를 직접 표시했다.
- 선택값은 “이미 정한 내용이 있다면 입력해 주세요” 펼침 영역으로 이동했다.
- 입력한 선택값이 AI가 변경할 수 없는 사용자 확정 조건임을 설명한다.
- 진행 화면을 Safety 확인과 AI 해석 단계로 변경했다.
- 확인 화면에서 사용자 입력과 AI 해석을 별도 출처 Badge로 표시한다.
- AI 해석 수정과 “이대로 진행” 확정을 제공한다.
- Safety 차단 화면에서 안전한 설명과 Seed 재입력 Action을 제공한다.
- 기존 법률 상세 후속 질문 문구를 제거했다.

### 비동기·상태

- 기존 TaskRun, worker, JobEvent, SSE 복구 경계를 재사용했다.
- 사용자 진행 Event 의미를 Safety Review, Idea Interpretation, Interpretation 저장으로 변경했다.
- `SAFETY_BLOCKED`를 프로젝트 모듈 상태의 현재 입력 필요 상태로 투영했다.

## 실제 실행한 검사

### Backend

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldCatalogTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldInvariantTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefReadinessTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefDerivationCommitServiceTests"
```

- 결과: 성공
- 테스트: 9개 통과
- 이 실행에서 `compileJava`, `compileTestJava`도 성공

첫 `compileJava` 실행은 Sandbox 네트워크 제한으로 Gradle 배포 파일 다운로드에 실패했다. 승인된 재실행에서는 실제 컴파일까지 진행되어 새 enum을 반영하지 않은 module status switch 오류 1건을 발견했고 수정했다. 최종 Backend 표적 실행에서 compile과 test가 모두 성공했다.

### AI

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py -q
```

- 결과: `6 passed`
- 시스템 기본 Python과 번들 Python에는 pytest가 없어 실패했으며, 저장소 `.venv`로 실행해 통과했다.

### Frontend

```powershell
npm.cmd run test:run -- src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/IdeaBriefReview.test.jsx src/features/idea-intake/components/MissingRequiredFieldsForm.test.jsx src/features/idea-intake/hooks/useIdeaIntake.test.jsx
```

- 결과: 4 files, 14 tests 통과

```powershell
npm.cmd run test:run -- src/shared/async-events/jobEventMessages.test.js
```

- 결과: 1 file, 2 tests 통과

```powershell
npx.cmd eslint src/features/idea-intake
```

- 결과: 성공

### 공통

- active Idea 코드의 구 필수 field 검색: 결과 없음
- `git diff --check`: exit code 0, AI Python 파일의 CRLF 변환 안내만 출력

## 의도적으로 생략한 검사

- Backend 전체 test와 전체 postgresTest/Testcontainers
- PostgreSQL baseline reset·migration 실행
- AI 전체 pytest와 실제 Provider smoke
- Frontend 전체 Vitest, 전체 ESLint, production build
- Docker rebuild/E2E
- 브라우저·모바일·접근성 수동 검증
- CI 전체 실행

LOCAL_FAST_EXECUTION_PROFILE과 V2-1 범위에 따라 사용자 검증으로 남겼다.

## 남은 위험

- `IdeaSafetyReview`와 `IdeaInterpretation`은 현재 `idea_briefs`의 명시적 typed/JSON column으로 저장한다. 별도 table 분리가 필요한지는 후속 migration 설계에서 결정해야 한다.
- 기존 baseline SQL을 V2로 수정했으므로 기존 로컬 DB에는 새 column/check가 자동 반영되지 않는다. 현재 rebuild의 DB reset 원칙에 따라 새 baseline으로 초기화해야 한다.
- 실제 Provider가 새 strict Safety/Interpretation schema를 안정적으로 생성하는지는 검증하지 않았다.
- Safety 분류는 법률 적격 판정이 아니며 V2-3 Legal Review를 대체하지 않는다.
- 참고 파일 UI는 유지했지만 기존과 마찬가지로 이 화면에서 실제 저장 파일 ID를 만드는 업로드 연결은 이번 Unit 범위가 아니다.
- Concept Factory와 Legal assembler는 아직 V2 Candidate/Legal Fact Pattern으로 전환되지 않았다. V2-2·V2-3 전에는 전체 파이프라인 runtime이 완성된 상태가 아니다.
- 기존 Idea 관련 비표적 통합 테스트 일부는 구 결과 fixture를 포함할 수 있으며 전체 회귀는 수행하지 않았다.

## 정확한 다음 시작점

다음 Unit은 `V2-2 — CONCEPT-CANDIDATE-V2-AND-DISTINCTNESS`다.

시작 시 다음을 먼저 조사한다.

1. 현재 `ConceptFactoryExecutionService`, Candidate provider schema, Slot/Attempt bounded rule
2. `SelectedConceptSnapshot`과 현재 candidate JSON의 구 field 의존
3. Prompt와 Backend 양쪽의 Seed LOCKED validation 연결점
4. distinctness를 Legal API 이전에 배치할 수 있는 실행 순서
5. pre-market SOM hypothesis와 source/decision persistence

V2-1 범위에 따라 V2-2 구현은 시작하지 않았다.
