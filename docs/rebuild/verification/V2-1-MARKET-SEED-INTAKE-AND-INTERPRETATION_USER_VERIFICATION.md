# V2-1 — Market Seed 입력 및 AI 해석 사용자 검증

## 목적

필수 Seed 3개, optional LOCKED 값, Safety 차단, AI Interpretation 확인·수정 흐름을 실제 DB·Provider·브라우저에서 검증한다.

## 예상 소요

- 로컬 DB 초기화와 서비스 기동: 5~15분
- API·브라우저 확인: 10~20분
- 실제 Provider 응답 대기: 환경에 따라 1~5분

## 1. 사전 조건

- 브랜치가 `rebuild/new-pipeline-v1`인지 확인한다.
- V2 baseline은 기존 Idea table 구조를 변경하므로 보존할 production data가 없는 rebuild DB에서만 초기화한다.
- Backend, AI, Frontend 환경변수와 Provider key를 정상 설정한다.

```powershell
git branch --show-current
git status --short
```

## 2. 가벼운 자동 검증 재실행

### Backend

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldCatalogTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldInvariantTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefReadinessTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefDerivationCommitServiceTests"
```

성공 기준: `BUILD SUCCESSFUL`.

### AI

```powershell
cd ai
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py -q
```

성공 기준: `6 passed`.

### Frontend

```powershell
cd frontEnd
npm.cmd run test:run -- src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/IdeaBriefReview.test.jsx src/features/idea-intake/components/MissingRequiredFieldsForm.test.jsx src/features/idea-intake/hooks/useIdeaIntake.test.jsx src/shared/async-events/jobEventMessages.test.js
npx.cmd eslint src/features/idea-intake
```

성공 기준: 모든 지정 test file 통과, ESLint 출력 없음.

## 3. DB 초기화 및 서비스 기동

프로젝트의 `LOCAL_RUN.md`와 현재 compose profile을 사용해 새 baseline DB를 만든다. 기존 로컬 DB를 삭제하면 데이터가 사라지므로 필요한 데이터가 없는지 먼저 확인한다.

기동 후 Backend log에서 다음 오류가 없어야 한다.

- `ck_idea_brief_status` 위반
- `ck_idea_field_decision` 위반
- `ck_idea_field_provenance` 위반
- `safety_decision` 또는 `interpretation_json` column 누락

## 4. 브라우저 — 최소 Seed 3개

프로젝트의 `/idea` 화면을 연다.

확인 항목:

1. 필수 항목이 아이디어 개요, 해결하려는 문제, 예상 사용자 세 개만 보인다.
2. 세 항목 중 하나라도 비우면 해당 입력 오류가 표시된다.
3. 플랫폼 역할, 결제, 개인정보, 물리활동, 파트너·자격이 초기 필수 입력으로 보이지 않는다.
4. 선택 영역을 모두 비워도 `안전 확인 및 AI 해석`을 실행할 수 있다.

## 5. 브라우저 — optional LOCKED 값

새 Seed에서 다음 예시를 입력한다.

- 아이디어 개요: 지역 식당의 음식물 폐기를 줄이는 서비스
- 해결하려는 문제: 영업 종료 후 음식물 폐기량이 많음
- 예상 사용자: 서울 지역 소규모 식당
- 대상 지역: 대한민국 서울
- 수익 모델: 월 정기 구독
- 가격: 월 9,900원
- 채널: 웹 직접 판매
- 예산 제약: 초기 검증 예산 500만원

AI 해석 완료 후 확인한다.

1. 입력한 선택값에 `사용자가 입력` Badge가 보인다.
2. 값이 `월 9,900원`에서 “합리적인 유료 모델”처럼 바뀌지 않는다.
3. API 응답 field의 `provenance`가 `USER_INPUT`, `decisionState`가 `LOCKED`다.
4. 입력하지 않은 optional field는 확정값으로 생성되지 않는다.

## 6. 브라우저 — Safety 허용과 해석 확인

일반적인 합법 사업 Seed로 실행한다.

성공 기준:

- 진행 문구가 안전 확인 → AI 해석 → 저장 의미로 표시된다.
- 확인 화면 제목이 `입력하신 아이디어를 이렇게 이해했습니다.`다.
- Safety 사유가 사용자에게 안전한 문구로 표시된다.
- 문제, 예상 사용자, 사용 맥락, 업종, 조사 범위, 한 줄 정의가 보인다.
- AI 해석에는 `AI가 해석` Badge가 보이고 수정할 수 있다.
- 해석을 수정하고 `이대로 진행`을 누르면 확정 상태가 된다.
- API 응답 Interpretation의 `source`는 `AI_DERIVED`, `authority`는 `REVIEWABLE`이다.

## 7. 브라우저 — Safety 차단

테스트용 별도 프로젝트에서 명백한 범죄 실행 지원 또는 개인정보 무단감시 목적의 Seed를 입력한다. 실제 피해를 유발하는 상세 절차를 작성하지 않는다.

성공 기준:

- 상태가 `SAFETY_BLOCKED`가 된다.
- Concept 생성으로 진행하지 않는다.
- 사용자에게 안전한 재구성 안내만 표시한다.
- Prompt, 내부 policy, raw model reasoning, stack trace가 표시되지 않는다.
- `아이디어 다시 입력`으로 Seed를 수정할 수 있다.

## 8. 후속 질문 제한 확인

문제·사용자 의미가 명확한 Seed에서는 후속 질문 없이 AI 해석 확인으로 이동해야 한다.

의도적으로 문제와 사용자가 서로 모순되는 Seed를 사용하면 후속 질문이 생길 수 있지만, 질문 대상은 다음 세 개 중 하나여야 한다.

- `ideaOverview`
- `problem`
- `targetUsers`

`payment`, `personalData`, `physicalActivity`, `requiredPartners`, 플랫폼 역할 질문이 나오면 실패다.

## 9. 새로고침과 작업 센터

AI 실행 중 다른 페이지로 이동하거나 새로고침한다.

성공 기준:

- 현재 TaskRun과 Job Event가 복원된다.
- 완료 후 Query API에서 Safety와 Interpretation이 나타난다.
- 해결된 과거 `NEEDS_INPUT`이 현재 작업으로 계속 표시되지 않는다.
- Provider raw error와 secret은 작업 센터에 나타나지 않는다.

## 10. 실패 시 수집할 로그

- Backend: Idea Brief Controller/Service/Worker와 TaskRun ID 주변 log
- AI: `IDEA_BRIEF_DERIVATION`의 safe error code와 schema validation 오류
- Browser: Network 탭의 `/api/v3/projects/{projectId}/idea-brief` 요청·응답
- Job Event: jobId, sequence, stageKey, status, safeMessageKey

수집 금지:

- API key·Authorization header
- 전체 Provider request/response body
- 실제 개인정보가 포함된 Seed 원문

## 11. 다음 단계 진행 가능 조건

- 필수 세 field만으로 Safety와 Interpretation을 완료할 수 있다.
- optional 미입력 진행과 optional 입력 LOCKED 보존이 모두 확인된다.
- Safety 차단이 Concept 진행을 막는다.
- AI Interpretation 수정·확정이 동작한다.
- 법률 상세 누락 때문에 초기 `NEEDS_INPUT`이 발생하지 않는다.
- 새 baseline DB와 실제 Provider에서 치명 오류가 없다.

위 조건을 충족하면 V2-2 ConceptCandidateV2와 distinctness 구현을 시작할 수 있다.
