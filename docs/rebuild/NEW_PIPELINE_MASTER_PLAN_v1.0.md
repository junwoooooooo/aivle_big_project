# AI 사업검증 플랫폼 전면 재구축 통합 기획서 v1.0

- 대상 저장소: `chamgo260210/bp_new_3`
- 분석 기준: `main` / `2b4871e210253309f08b164c6dfddefc4ce5d0bc`
- 마케팅 이식 참고: `junwoooooooo/aivle_big_project` / `AIdev` / `581234abbcd77ab5931744be15fa7f28a272e58e`
- 문서 상태: 재구축 기준안
- 작성일: 2026-08-06

## 0. 문서의 목적과 효력

이 문서는 기존 단계형 Journey와 대화형 Workspace를 중심으로 구축하던 방향을 중단하고, 새로운 제품 파이프라인·UI/UX·데이터 정본·외부 모듈 연결·비동기 실행·저장소 정리·DB 초기화·테스트 기준을 하나로 고정한다.

본 문서 승인 이후 신규 구현의 우선순위는 다음과 같다.

1. 본 통합 기획서
2. `NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
3. `NEW_PIPELINE_UI_UX_SPEC_v1.0.md`
4. 데이터·API·외부 모듈 계약 문서
5. 저장소 정리 및 구현 계획
6. 단계별 실행 지시문
7. 진행 결과 문서

`docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_*`와 G0~G6 문서는 과거 구현 근거로만 보존하며 신규 제품 계약을 변경하지 않는다.

---

## 1. 재구축 배경

현재 저장소는 전역 `project.stage`와 Journey Route를 중심으로 다음 순서를 전제한다.

`아이디어 입력 → 아이디어 해석 → 법률·규제 검토 → 컨셉 생성 → 컨셉 분석 → 컨셉 선택 → 페르소나 → 인터뷰 → 마케팅 → 최종 보고서`

현재 `frontEnd/src/features/projects/model/projectWorkflowModel.js`와 `projectRoutes.js`에는 이 순서가 직접 반영되어 있다. 삭제된 G5·G6은 법률 Boundary를 먼저 확정한 뒤 소수 후보를 생성·공개하는 구조였다. 이 구조는 새 제품 방향과 맞지 않고, 외부 분석 모듈을 단계 사이에 결합하기 어렵다.

새 방향에서는 별도 법률검토 단계를 없애고, 컨셉 생성과 법률검토를 하나의 5개 Slot 팩토리로 통합한다. 사용자는 컨셉 하나를 선택해 외부 시장분석으로 넘기고, 시장분석에서 제안된 변경안을 검토해 최종 기획을 확정한다. BM·재무 분석과 페르소나 응답 테스트는 외부 모듈로 연결하며, 마케팅은 최종 확정 기획을 받아 콘텐츠만 제작한다.

---

## 2. 제품 목표

사용자가 막연한 아이디어를 입력하면 서비스는 다음 결과까지 연결한다.

`아이디어 정리 → 법률검토 통과 컨셉 5개 → 비교·선택 → 시장분석·기획 확정 → BM·재무 분석 + 페르소나 응답 테스트 → 마케팅 콘텐츠`

핵심 목표는 다음과 같다.

- 초기 입력 부담을 낮춘다.
- 법적으로 실행 가능한 컨셉만 공개한다.
- 컨셉 후보 생성·검증·재설계·교체 과정을 사용자에게 투명하게 보여준다.
- 최종 확정 기획을 모든 후속 모듈의 단일 정본으로 사용한다.
- 외부 분석 모듈을 저장소 내부 Entity나 전역 Stage에 결합하지 않는다.
- UI는 복잡한 단계 잠금보다 현재 상태와 필요한 다음 행동을 설명한다.
- 실제로 동작하지 않는 기능·이전 메뉴·중복 Workspace는 과감히 제거한다.

---

## 3. 최종 사용자 단계

| 번호 | 사용자 표시명 | 핵심 결과 | 담당 경계 |
|---|---|---|---|
| 1 | 아이디어 정리 | 확정 Idea Brief | 직접 구현 |
| 2 | 컨셉 생성·법률검토 | 법률검토 통과 컨셉 5개 | 직접 구현 |
| 3 | 컨셉 비교·선택 | Selected Concept Snapshot | 직접 구현 |
| 4 | 시장분석·기획 확정 | Finalized Planning Snapshot | 외부 모듈 연결 |
| 5 | BM·재무 분석 + 페르소나 응답 테스트 | 분석·응답 결과 | 외부 모듈 연결 |
| 6 | 마케팅 콘텐츠 제작 | 콘텐츠 결과물 | 직접 UI 구축·선별 이식 |

5단계 보조 문구는 다음으로 고정한다.

> 확정된 기획의 비즈니스 모델과 재무 구조를 분석하고, 경쟁상품 대비 반응을 가상 페르소나 응답으로 확인합니다.

기술·운영 분석은 사용자 단계와 구현 범위에서 제외한다.

---

## 4. 직접 구현 범위와 외부 모듈 범위

### 4.1 직접 구현

- 프로젝트 공통 Shell과 새로운 내비게이션
- Idea Brief 입력·AI 구조화·후속 질문·확정
- 공통 법률 Context Pack
- 5개 Slot 컨셉 팩토리
- 후보 생성·구조 검증·법률 적용 검토·재설계·교체
- 컨셉별 법률 평가·Evidence
- 컨셉 비교·선택
- 시장분석 Handoff Snapshot
- 외부 모듈 실행 상태 Shell
- 시장분석 변경 제안 검토 UI
- Finalized Planning Snapshot
- 전역 작업 센터와 비동기 진행 표시
- 마케팅 콘텐츠 목록·생성·편집·저장·다운로드

### 4.2 외부 모듈

이번 재구축에서 내부 알고리즘을 구현하지 않는다.

- 시장 규모·고객·경쟁 분석
- 시장분석 기반 기획 변경안 생성
- BM 분석
- 재무분석
- 경쟁상품 비교 인터뷰지 생성
- 페르소나 응답 생성
- 응답 집계·해석

우리 저장소는 입력 Snapshot, 상태, Event, 결과 Reference, 결과 요약, 변경 제안, 결과 Hash만 계약으로 관리한다.

---

## 5. 새로운 정보구조

### 5.1 Route

```text
/app/projects/{projectId}/overview
/app/projects/{projectId}/idea
/app/projects/{projectId}/concepts
/app/projects/{projectId}/concepts/compare
/app/projects/{projectId}/market
/app/projects/{projectId}/business-persona-test
/app/projects/{projectId}/marketing
/app/projects/{projectId}/settings
```

### 5.2 Desktop 내비게이션

- 프로젝트 개요
- 1. 아이디어 정리
- 2. 컨셉 생성·법률검토
- 3. 컨셉 비교·선택
- 4. 시장분석·기획 확정
- 5. BM·재무 분석 + 페르소나 응답 테스트
- 6. 마케팅 콘텐츠 제작
- 프로젝트 설정

각 메뉴는 독립 모듈 상태 Badge를 표시한다.

### 5.3 접근 정책

모든 페이지 진입을 허용한다. 전제조건이 없으면 화면을 차단하지 않고 필요한 입력과 이동 Action을 안내한다. 실제 실행 Action에서만 최소 전제조건을 검사한다.

---

## 6. 전역 상태 모델

프로젝트 전체를 하나의 `stage` 값으로 통제하지 않는다.

모듈:

- IDEA
- CONCEPT_FACTORY
- CONCEPT_SELECTION
- MARKET_ANALYSIS
- BUSINESS_PERSONA_TEST
- MARKETING

모듈별 상태:

- NOT_READY
- READY
- QUEUED
- RUNNING
- NEEDS_INPUT
- COMPLETED
- FAILED
- STALE
- NOT_CONNECTED

`NOT_CONNECTED`는 외부 모듈이 아직 결합되지 않았음을 표현한다. 이는 오류나 접근 차단 상태가 아니다.

---

## 7. 1단계: 아이디어 정리

### 7.1 UX 흐름

`개요 입력 → AI 구조화 → 후속 질문 → Brief 검토 → 사용자 확정`

현재 대화형 Workspace UI는 제거한다. 대신 자유 입력 Form과 Question Card를 사용한다. 추후 Chat을 재도입할 수 있도록 Form과 Chat 모두 동일한 Idea Brief Command API를 사용한다.

### 7.2 주요 입력

- 아이디어 개요
- 해결하려는 문제
- 예상 사용자
- 서비스 지역
- 원하는 결과
- 반드시 지킬 조건
- 피하고 싶은 방식
- 참고 파일

### 7.3 후속 질문

한 번에 2~4개를 표시하고 자유 입력·단일 선택·복수 선택·아직 결정하지 않음을 지원한다.

### 7.4 Brief Field 그룹

- 사업 아이디어
- 사업 조건
- 규제 민감 정보

사용자 표시 출처는 `사용자 입력`, `파일에서 추출`, `AI 제안`, `미정`으로 단순화한다.

### 7.5 완료

`이 내용으로 컨셉 만들기`를 누르면 불변 `IdeaBriefSnapshot`을 생성한다.

---

## 8. 2단계: 컨셉 생성·법률검토

### 8.1 내부 흐름

`Idea Brief → 공통 법률 Context → 5개 방향 후보 → 구조 검증 → 컨셉별 법률 검토 → 통과 누적 → 재설계 또는 폐기 → 대체 후보 → 5개 완료`

### 8.2 5개 차별화 방향

| Slot | 방향 |
|---|---|
| 1 | 고객 경험과 사용 방식 |
| 2 | 운영 모델과 파트너 구조 |
| 3 | 수익 모델과 가격 |
| 4 | 유통·채널과 확장 방식 |
| 5 | 저위험·빠른 실행 방식 |

### 8.3 법률 Context Pack

Idea Brief 기준으로 업종, 지역, 플랫폼 역할, 거래, 결제, 개인정보, 물리 활동, 자격·허가, 표시·광고와 공식 Evidence를 한 번 구축한다.

### 8.4 컨셉별 법률 검토

각 컨셉에 대해 활동, 주체, 플랫폼 역할, 금지 활동, 파트너, 자격·허가, 필수 통제, 필수 고지, 금지 변형, 미확인 사실, 적용 Evidence를 판정한다.

### 8.5 상태

내부:

- IMPLEMENTABLE
- IMPLEMENTABLE_WITH_CONTROLS
- NEEDS_FACTS
- REDESIGNABLE
- REJECTED

사용자 공개:

- 구현 가능
- 필수 조건을 반영하면 구현 가능

### 8.6 반복 상한

- 초기 후보 5개
- 후보별 법률 재설계 최대 1회
- 대체 후보 Round 최대 2회
- 전체 검사 후보 최대 15개

5개를 억지로 채우지 않는다. 부족한 사실이나 사용자의 고정 조건 때문에 5개가 불가능하면 확인 필요 상태로 종료한다.

---

## 9. 컨셉 Workboard UI

### 9.1 Desktop

- 상단: 통과 수, 총 검사 후보 수, 재설계·폐기·교체 수
- 중앙: 5개 Slot, 3+2 Grid
- 오른쪽: 전체 Timeline과 Slot 필터

### 9.2 사용자 상태 문구

- 후보를 만들고 있습니다.
- 사업 구조를 정리하고 있습니다.
- 법률 근거를 확인하고 있습니다.
- 필수 통제를 반영하고 있습니다.
- 운영 역할을 다시 설계하고 있습니다.
- 부적합 후보를 다른 방향으로 교체하고 있습니다.
- 법률검토를 통과했습니다.

### 9.3 공개 Gate

진행 중에는 전체 Draft를 공개하지 않는다. Slot 통과 여부만 표시하고 적격 5개가 모두 준비된 뒤 상세를 동시에 공개한다.

### 9.4 표시 금지

Prompt, Provider 원문, Stack Trace, 내부 JSON, 기술 코드, 폐기 Draft 전체, 법률 원문 전체를 표시하지 않는다.

---

## 10. 컨셉 상세 계약

### 기본 기획

- 컨셉명과 한 줄 설명
- 대상 고객과 문제 상황
- 핵심 가치와 해결 방식
- 사용 흐름과 주요 기능
- 플랫폼 역할과 참여 주체
- 거래·데이터 흐름

### 사업 구조

- 운영 모델
- 필요한 파트너
- 채널·가격·수익 모델 가설
- 주요 비용 요인
- 초기 실행 범위
- 가정과 위험

### 법률 검토

- 구현 가능성
- 검토 활동
- 필수 통제·파트너·자격·고지
- 금지 운영 방식
- 확인 필요 사실
- 전문가 검토 권장 여부
- Evidence와 기준일

사용자 표현은 `공식 근거 기반 법률 구현 가능성 검토`로 고정한다.

---

## 11. 3단계: 컨셉 비교·선택

### 11.1 카드 보기

컨셉명, 차별점, 고객, 운영, 수익, 법률 상태, 필수 조건, 핵심 위험을 요약한다.

### 11.2 비교표

대상 고객, 문제, 가치, 사용 흐름, 플랫폼 역할, 기능, 수익, 파트너 의존, 법률 조건, 초기 실행 범위를 행으로 비교한다.

단일 종합점수로 순위를 강제하지 않는다. `빠른 실행`, `파트너 의존 높음`, `반복 수익형`, `규제 통제 필요` 같은 설명형 Tag를 사용한다.

### 11.3 선택

`컨셉 선택 → 선택 이유 → 법률 필수 조건 확인 → 전달 내용 확인 → 확정`

확정 시 불변 `SelectedConceptSnapshot`을 만든다.

---

## 12. 4단계: 시장분석·기획 확정

외부 시장분석 모듈이 연결되지 않았을 때도 페이지는 보인다. `연결 준비 중`, 선택 컨셉, 입력 Snapshot, 전달 예정 항목을 표시한다.

연결 후 상태:

- 전달 준비
- 요청 접수
- 분석 대기
- 분석 중
- 결과 정리 중
- 완료
- 실패
- 최신 기획과 다름

시장분석 결과에서 수신할 최소 내용은 Run ID, 입력 Snapshot, 시장 요약, 타깃 고객 근거, 경쟁상품, 가격·채널 시사점, 기획 변경 제안, Evidence, 결과 Hash다.

---

## 13. 의미 기반 기획 변경 UX

사용자에게 `v1`, `v2`, `v3`를 주 이름으로 표시하지 않는다.

변화 단계:

- 선택한 원안
- 시장분석 제안
- 시장분석 반영안
- 최종 확정 기획
- 이전 기획

변경 카드에는 현재, 제안, 이유, 영향받는 부분, 근거를 표시한다. 사용자는 `채택`, `일부 채택`, `거절` 중 선택한다.

반영안 이름 예시:

- 시장분석 반영안 — 타깃·운영모델 조정
- 시장분석 반영안 — 가격·채널 조정
- 시장분석 반영안 — 초기 출시범위 축소

내부 Revision Sequence는 Metadata에서만 `내부 기록 #4`처럼 작게 표시한다.

시장분석 수정 Round는 기본 1회, 필요한 Delta Refresh 1회로 제한한다. 추가 Round는 사용자의 명시적 요청으로만 만든다.

---

## 14. Finalized Planning Snapshot

시장분석 변경안을 반영하고 사용자가 최종 확정하면 불변 Snapshot을 만든다.

포함 내용:

- 최종 컨셉 구조
- 채택·거절된 변경
- 최종 타깃·가치·기능·채널
- 가격·수익·운영 가설
- 법률 통제·금지 표현·필수 고지
- 경쟁상품 차별점
- 원본 Snapshot과 결과 Hash

이 Snapshot은 BM·재무, 페르소나 응답, 마케팅의 단일 정본이다.

---

## 15. 5단계: BM·재무 분석 + 페르소나 응답 테스트

이번 저장소에서는 외부 모듈 통합 Shell만 구현한다.

세 영역:

- BM 분석
- 재무분석
- 페르소나 응답 테스트

외부 모듈이 없으면 각 영역에 `연결 준비 중`과 준비된 입력 Snapshot을 표시한다. 결과가 연결되면 상태, 요약, 상세 결과 Reference를 표시한다.

페르소나 결과는 실제 시장 확률이 아니라 경쟁상품과 기획 사이의 상대 비교 결과로 설명한다.

이 단계는 기획 변경을 자동 제안하거나 적용하지 않는다.

---

## 16. 6단계: 마케팅 콘텐츠 제작

입력:

- Finalized Planning Snapshot
- 시장분석 핵심 시사점
- 법률상 허용 주장·금지 표현·필수 고지

BM·재무 결과와 페르소나 응답을 필수 입력으로 요구하지 않는다.

### 콘텐츠 유형

- SNS 게시물
- 광고 카피
- 랜딩페이지
- 블로그·소개글
- 이메일
- 배너
- 포스터
- 이미지 생성용 기획

### Desktop UI

- 왼쪽: 생성 설정
- 중앙: Canvas·Preview
- 오른쪽: 스타일·문구 편집·법률 주의사항

### 생성 이력 사용자 이름

- 첫 생성안
- 친근한 톤 수정안
- 짧은 SNS 문구안
- 법률 고지 반영안
- 최종 저장본

### AIdev 선별 이식

가져올 대상은 Marketing API, Generation Task, Generation Prompt, Canvas, Setup Panel, Style Panel, Copy Editor, Renderer, Generation Hook, Content·Version·Types다.

가져오지 않을 대상은 비교·A/B 테스트, Persona 평가, Panel Interview, Market Response 의존, 출시 전략 리포트, 캠페인 단계다.

---

## 17. 비동기 실행과 사용자 진행 표시

유지:

- TaskRun
- Worker Lease
- Retry·Recovery
- Job Event
- SSE
- Polling fallback
- Replay
- Idempotency

전역 Header에 작업 센터를 제공한다. 사용자는 작업 중 다른 페이지로 이동할 수 있다.

Event는 갱신 신호이고 Query API가 화면 정본이다.

금지:

- 가짜 퍼센트
- 2초 고정 Polling
- Prompt·Provider Raw Body·Stack Trace·Key·Authorization 노출

새로고침은 현재 Run, Slot·외부 Run, Event Replay, Query 복원, SSE 재연결 순서로 복원한다.

---

## 18. 외부 모듈 연결 원칙

외부 모듈은 우리 DB Entity를 직접 읽지 않는다.

`Snapshot 생성 → Module Handoff → External Run ID → 상태·Event → Result Reference·Hash`

표준 상태 Event:

- module.accepted
- module.queued
- module.started
- module.progress
- module.completed
- module.failed

상세 계약은 `EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md`와 JSON Schema를 따른다.

---

## 19. 핵심 데이터 모델

### 프로젝트·아이디어

- projects
- idea_briefs
- idea_brief_fields
- idea_questions
- idea_answers
- attachments

### 법률

- legal_context_packs
- legal_evidence
- legal_context_evidence_links

### 컨셉

- concept_factory_runs
- concept_slots
- concept_attempts
- concepts
- concept_legal_assessments
- concept_legal_evidence_links
- concept_rejection_summaries
- concept_selections

### 기획 변화

- planning_change_proposals
- planning_change_decisions
- planning_snapshots
- finalized_planning_snapshots

### 외부 모듈

- module_handoffs
- module_runs
- module_results
- module_events

### 마케팅

- marketing_content_requests
- marketing_contents
- marketing_content_revisions
- marketing_assets

### 공통

- task_runs
- job_events
- audit_events

---

## 20. API 방향

신규 API는 `/api/v3`로 분리한다. 기존 `/api/v1`, `/api/v2`와 혼용하지 않는다.

### Idea Brief

- `GET /api/v3/projects/{projectId}/idea-brief`
- `POST /api/v3/projects/{projectId}/idea-brief/derive`
- `PATCH /api/v3/projects/{projectId}/idea-brief/fields`
- `POST /api/v3/projects/{projectId}/idea-brief/answers`
- `POST /api/v3/projects/{projectId}/idea-brief/confirm`

### Concept Factory

- `POST /api/v3/projects/{projectId}/concept-factory-runs`
- `GET /api/v3/projects/{projectId}/concept-factory-runs/current`
- `GET /api/v3/projects/{projectId}/concept-factory-runs/{runId}`
- `GET /api/v3/projects/{projectId}/concept-factory-runs/{runId}/slots`
- `POST /api/v3/projects/{projectId}/concept-factory-runs/{runId}/resume`
- `POST /api/v3/projects/{projectId}/concept-factory-runs/{runId}/retry`
- `GET /api/v3/projects/{projectId}/concepts`

### Selection·Planning·Modules·Marketing

세부 API는 계약 문서에서 고정한다.

---

## 21. UI 디자인 시스템

### 시각 방향

- 밝은 Neutral 기반
- 넓은 진한 색 면적 최소화
- Primary Color는 주요 Action 중심
- 상태는 색상·Icon·문구 병행
- 긴 정보는 Progressive Disclosure
- 인쇄·캡처에서도 번지지 않는 대비

### Typography

- 프로젝트 제목: 24~28px
- 현재 단계: 18~20px
- Section: 15~16px
- Card 제목: 14~15px
- 본문: 13~14px
- Helper·Metadata: 11.5~12.5px

### 반응형

- Desktop: Sidebar 240px, Workboard 3+2, Marketing 3열
- Tablet: Workboard 2열, 설정 Drawer
- Mobile: 단일 열, 현재 단계 Selector, Sticky Primary Action, 세로 비교 Card

### 접근성

키보드, Focus, aria-live, alert, accordion, 색상 외 문구, 44px Touch Target, Reduced Motion, Modal Focus Trap, Screen Reader Label을 필수 적용한다.

---

## 22. 현재 저장소 정리 원칙

### 22.1 문서

`docs/redesign/**` 전체는 `docs/archive/conversational-workspace/**`로 이동한다. 신규 구현의 정본은 `docs/rebuild/**`다.

현재 내부 AI v1 계약과 Fixture는 새 API·외부 Handoff가 완료될 때까지 유지하되, 신규 구현에서 참조하지 않는다. Cutover 후 `docs/archive/contracts/internal-ai-v1/**`로 이동한다.

### 22.2 Frontend

즉시 교체:

- `frontEnd/src/features/projects/routing/projectRoutes.js`
- `frontEnd/src/features/projects/model/projectWorkflowModel.js`
- 기존 Journey Navigation·Stepper

단계별 제거:

- Conversational Idea UI
- 3 Concept Workboard
- Legacy Concept Journey UI
- Validation·Persona·Interview·Market Response UI
- 기존 Financial UI
- 기존 Marketing Workspace
- Final Report Journey

새 구현은 `frontEnd/src/features/idea-intake`, `concept-factory`, `concept-selection`, `market-integration`, `planning-revision`, `business-persona-integration`, `marketing-content`, `job-center`에 둔다.

### 22.3 Backend

유지:

- auth, project, taskrun, jobevent, audit, file storage

신규:

- `backend/.../pipeline/idea`
- `pipeline/concept`
- `pipeline/legal`
- `pipeline/selection`
- `pipeline/planning`
- `pipeline/integration`
- `pipeline/marketing`

기존 `journey`, `validation`, legacy financial·marketing service는 신규 코드에서 import하지 않는다. 새 대체 기능이 완료되는 단계마다 삭제한다.

### 22.4 AI

Provider Adapter는 유지하되 Provider Output Schema 검증과 Smoke Gate를 표준화한다.

새 Task Package:

- idea_brief
- concept_candidate
- concept_legal_review
- concept_redesign
- marketing_content

기존 Journey Prompt는 archive 후 신규 코드에서 참조하지 않는다. AIdev의 `marketing_generation`만 선별 이식한다.

### 22.5 DB

보존 데이터가 없으므로 기존 개발 DB Volume을 삭제하고 새 Baseline Migration으로 시작한다. User, Auth, Project, File, Audit, TaskRun, JobEvent는 보존하고 나머지는 새 Pipeline 기준으로 재정의한다.

---

## 23. Hard Cutover 원칙

이전 기능이 새 UI에 보이는 상태로 장기간 공존하지 않는다.

- R1에서 기존 Route와 Navigation을 먼저 끊는다.
- 각 신규 기능이 완성되는 즉시 대응 Legacy 기능을 삭제한다.
- Legacy 코드는 신규 Package에서 import하지 않는다.
- Legacy Controller는 신규 Route에 등록하지 않는다.
- Legacy Table은 신규 FK의 대상이 되지 않는다.
- 참조 목적은 Git History와 `docs/archive`로 해결한다.

---

## 24. 재구축 단계

| 단계 | 범위 | 핵심 완료 Gate |
|---|---|---|
| R0 | 문서·계약·정리 기준 | 문서 승인, 파일 작업 Manifest 확정 |
| R1 | Hard Cutover Foundation | 새 Shell·Route·모듈 상태·새 DB Baseline |
| R2 | Idea Brief | 입력→질문→Brief 확정 E2E |
| R3 | 5 Concept Factory | 5 Slot·법률 Loop·5개 동시 공개 |
| R4 | 비교·선택·Handoff | 선택 Snapshot과 외부 Stub 전달 |
| R5 | 시장 결과·기획 변화·외부 Shell | 의미 기반 변경 UX와 Finalized Snapshot |
| R6 | 마케팅 콘텐츠 | AIdev 선별 이식과 새 Source Snapshot |
| R7 | Legacy 제거·전체 안정화 | 전체 회귀·Docker E2E·브라우저·접근성 |

기존 G7 이후 번호는 사용하지 않는다.

---

## 25. 테스트와 승인 기준

핵심 E2E:

`프로젝트 → 아이디어 입력 → 질문 → Brief 확정 → 5개 컨셉 생성 과정 → 5개 공개 → 비교·선택 → 시장 Stub → 변경안 판단 → 최종 기획 → 마케팅 생성·편집·저장`

필수 검증:

- 기존 Journey 메뉴 미노출
- 새 Route 직접 접근
- 전제조건 부족 안내
- Desktop·Tablet·Mobile
- 키보드·접근성
- 작업 중 페이지 이동
- 새로고침 복원
- SSE 재연결
- 외부 모듈 미연결 상태
- Stale Snapshot
- Retryable·Permanent 실패
- 5 Slot 진행
- 법률 근거
- 의미 기반 변화 이력
- Provider Schema Smoke

최종 완료 Gate에는 실제 Docker, 실제 OpenAI Provider Smoke, 실제 브라우저 E2E가 포함된다.

---

## 26. 고정 불변식

1. 현재 제품 UI에서 대화형 Workspace를 제거한다.
2. Idea Brief는 Form과 추후 Chat이 함께 사용할 독립 정본이다.
3. 별도 법률검토 사용자 단계는 없다.
4. 법률검토는 5개 Slot 컨셉 팩토리 내부 루프다.
5. 사용자는 내부 루프의 실제 진행을 본다.
6. 통과하지 못한 Draft 전체는 공개하지 않는다.
7. 컨셉 5개가 모두 준비된 뒤 상세를 공개한다.
8. 생성·재설계·교체에는 명확한 상한이 있다.
9. 컨셉 선택은 불변 Snapshot으로 시장분석에 전달한다.
10. 시장분석만 기획 변경을 제안할 수 있다.
11. 변경안은 자동 적용하지 않는다.
12. UI에는 의미 기반 이름을 사용한다.
13. Finalized Planning Snapshot이 후속 모듈 정본이다.
14. BM·재무와 페르소나 응답은 기획을 수정하지 않는다.
15. 외부 모듈은 Snapshot 계약으로 연결한다.
16. 프로젝트 전체를 단일 Stage로 잠그지 않는다.
17. 페이지는 열고 Action만 최소 조건을 검사한다.
18. TaskRun·JobEvent·SSE를 유지한다.
19. 가짜 진행률을 표시하지 않는다.
20. 마케팅은 최종 확정 기획을 기준으로 한다.
21. 기존 Persona·Panel·Market Response를 마케팅 필수 의존으로 사용하지 않는다.
22. 기존 Journey UI·Route는 노출하지 않는다.
23. 보존 데이터가 없으므로 DB와 Migration을 새 기준으로 정리한다.
24. Legacy는 신규 Package·Route·DB 관계에 섞이지 않는다.
25. 실제 Provider Smoke와 브라우저 E2E가 최종 Gate다.

---

## 27. 산출 문서와 배치

신규 기준 문서는 `docs/rebuild/` 아래에 둔다.

- `README.md`
- `NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `NEW_PIPELINE_UI_UX_SPEC_v1.0.md`
- `NEW_PIPELINE_INFORMATION_ARCHITECTURE_AND_NAVIGATION_v1.0.md`
- `NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md`
- `ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- `MARKETING_CONTENT_PORTING_PLAN_v1.0.md`
- `LEGACY_KEEP_REPLACE_DELETE_MATRIX_v1.0.md`
- `REPOSITORY_REORGANIZATION_AND_CUTOVER_PLAN_v1.0.md`
- `DATABASE_RESET_AND_BASELINE_PLAN_v1.0.md`
- `NEW_PIPELINE_IMPLEMENTATION_PLAN_v1.0.md`
- `TEST_AND_ACCEPTANCE_PLAN_v1.0.md`
- `REBUILD_EXECUTION_RULES_v1.0.md`
- `R0_R7_CODEX_EXECUTION_PROMPTS_v1.0.md`
- `decisions/DECISION_LOG.md`
- `progress/README.md`
- `contracts/*.schema.json`
- `REPOSITORY_FILE_OPERATION_MANIFEST.csv`

과거 문서는 `docs/archive/conversational-workspace/`로 이동한다.
