# V2-10A 사용자 검증

## 자동 표적 명령

```powershell
cd backend
.\gradlew.bat test --no-daemon --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldInvariantTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefDerivationCommitServiceTests" --tests "com.aivle.backend.pipeline.concept.ConceptGenerationStrategyPolicyTests" --tests "com.aivle.backend.pipeline.concept.ConceptCandidateV2ValidatorTests"

cd ..\ai
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py -q

cd ..\frontEnd
npm.cmd run test:run -- src/features/idea-intake/components/IdeaBriefReview.test.jsx src/features/idea-intake/model/ideaIntakeModel.test.js
npx.cmd eslint src/features/idea-intake
```

예상 1~4분. 모든 명령 exit code 0이 성공 기준이다.

## 브라우저 확인

1. optional 가격은 비우고 개요에 `서울 직장인을 대상으로 월 9,900원 구독 방식으로 제휴 풋살장에서 운영한다`를 입력한다.
2. Review에서 가격·지역·수익·채널 후보가 “AI 발견 · 확인 필요”로 보이고 아직 확정값 Badge가 아닌지 확인한다.
3. 가격을 확인하고 진행한 뒤 다시 조회해 `사용자 확인 · 확정됨`인지 확인한다.
4. 후보를 수정 후 확인하고 수정값이 LOCKED인지 확인한다.
5. 후보를 `결정하지 않음`으로 돌리면 OPEN이며 Concept 생성을 막지 않는지 확인한다.
6. optional 가격에 `월 12,000원`을 직접 입력하고 원문에 `월 9,900원`을 함께 적어도 12,000원이 유지되는지 확인한다.
7. 최소 세 필드만 입력한 run은 EXPLORE, 일부 후보 확인은 REFINE, 구체 원안과 복수 확인은 AS_IS인지 API/화면에서 확인한다.

실패 시 Idea Brief GET 응답의 `fields`, `interpretation.commitmentCandidates`, Concept run의 `generationStrategy`, 관련 TaskRun/JobEvent ID를 수집한다.
