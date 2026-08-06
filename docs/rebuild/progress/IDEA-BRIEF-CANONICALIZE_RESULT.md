# IDEA-BRIEF-CANONICALIZE Result

## Outcome

Idea Brief를 후속 질문 답변이 Canonical Field에 실제 반영되는 Planning Input으로 정리했다. overview 원문은 별도 `overview_text`에 저장되고 assumptions와 분리된다. Backend의 단일 Field Catalog가 15개 key, label, concept 필수 여부, 기본 결정 상태, 규제 민감 여부, 허용 질문 타입을 정의하며 Query API가 이 Catalog를 Frontend에 전달한다.

AI summary, contradiction, missing-field, readiness score/status 및 clarification round는 Brief에 보존된다. Readiness는 저장된 row 수가 아니라 전체 required Catalog, MISSING/빈 값, 미응답 active question, unresolved contradiction, AI 상태와 2회 clarification 상한으로 계산한다.

## Files changed

- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBrief.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefField.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefFieldCatalog.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaQuestion.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaQuestionRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefReadinessCalculator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefDerivationCommitService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/api/IdeaBriefApiModels.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefControllerTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefSnapshotTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefFieldCatalogTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefReadinessTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefCanonicalizationIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefDerivationCommitServiceTests.java`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.js`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.test.js`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.js`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.test.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaBriefReview.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaBriefReview.test.jsx`
- `frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx`
- `frontEnd/src/features/idea-intake/styles/idea-intake.css`
- `docs/rebuild/progress/IDEA-BRIEF-CANONICALIZE_RESULT.md`
- `docs/rebuild/verification/IDEA-BRIEF-CANONICALIZE_USER_VERIFICATION.md`

## Contracts implemented

- 15개 Canonical Field Catalog와 권장 default decision state.
- `overview_text` 및 AI assessment metadata를 Baseline V1과 JPA Entity에 추가; additive migration은 만들지 않음.
- overview를 assumptions로 복제하던 Frontend hydration 제거.
- Query API에 overview, Catalog, summary, contradiction, clarification metadata와 신규 readiness 구조 노출.
- required Catalog 전체 기준 missing/completed 계산과 persisted AI missing key 보존.
- 사용자 답변을 같은 transaction에서 Answer와 target Field에 반영하고 provenance를 `USER_CONFIRMED`로 설정.
- `__UNDECIDED__`/`**UNDECIDED**`를 빈 확정값이 아닌 `MISSING` Field로 기록.
- multi-select 답변을 중복 제거·정렬한 JSON 문자열로 정규화.
- 기존 질문/답변 기록은 보존하고 이전 round 질문만 inactive 처리.
- 답변 후 필요 시 정상 TaskRun/Job Event 경로로 derivation을 재queue하며 clarification round를 최대 2회로 제한.
- Catalog default에 따라 사용자 입력의 decision state를 적용하고 모든 사용자 입력을 자동 `LOCKED` 처리하던 Frontend 로직 제거.
- Confirm 전에 status, required missing, active unanswered question, blocking contradiction 및 readiness를 재검증.
- Confirm snapshot hash에 overview, fields, summary, contradiction을 포함하고 confirmed row 불변성 및 새 Draft sequence 경계를 유지.
- Review UI에 AI summary, 출처, 결정 상태 선택, missing field, contradiction, readiness score를 표시.
- Review 저장 후 최신 Domain Query 응답의 `readyForConfirm`이 true일 때만 confirm 호출.

## Checks actually run

- `backend\\gradlew.bat compileJava` — 최종 통과.
- Field Catalog, Readiness, Answer-to-Field transaction, clarification bound, Confirm Snapshot, Controller/Field invariant targeted tests — 통과.
- AI assessment commit-service 및 기존 Idea Brief Worker targeted tests — 통과.
- overview 분리와 persisted missing metadata 보강 후 canonical integration/readiness/commit targeted tests — 통과.
- `vitest` Idea Intake model, Review, Hook, Question Card targeted tests — 통과.
- 변경된 Idea Intake JS/JSX 대상 ESLint — 통과.
- `git diff --check` — 통과.

개발 중 첫 `compileJava`에서 중복 메서드가 감지되었고 즉시 제거했다. 이후 최종 compile 및 관련 테스트는 통과했다.

## Checks intentionally omitted

- 전체 backend test 및 전체 `postgresTest`.
- 전체 frontend baseline/production build.
- Docker build, browser E2E, 실제 provider smoke.
- DB reset과 기존 volume 삭제.
- 모바일·접근성 수동 검사.

## Remaining risks

- Baseline V1이 변경되었으므로 현재 개발 PostgreSQL volume에는 새 column이 없다. 사용자 검증 시 한 번의 clean DB reset이 필요하다.
- 실제 AI provider가 두 clarification round 동안 사용자 답변을 반영해 안정적인 strict schema를 반환하는지는 runtime smoke가 필요하다.
- 최대 round 이후 수동 Field 수정으로 contradiction을 해소하는 UX는 구현됐지만 실제 브라우저 흐름 검증은 하지 않았다.
- 기존 보존 데이터가 없다는 실행 단위 전제를 사용했으므로 운영 데이터 migration은 제공하지 않는다.

## Exact continuation point

`docs/rebuild/verification/IDEA-BRIEF-CANONICALIZE_USER_VERIFICATION.md`에 따라 PostgreSQL volume을 한 번 clean reset한 뒤 derive → 질문 답변 → 최대 2회 follow-up → review 수정 → confirm → confirmed snapshot 이후 편집 시 새 Draft sequence를 검증한다. 이 runtime acceptance 전에는 다음 실행 단위로 진행하지 않는다.
