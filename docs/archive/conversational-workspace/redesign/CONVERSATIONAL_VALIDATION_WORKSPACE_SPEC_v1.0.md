**대화형 AI 사업검증 워크스페이스**

제품·UX·도메인·AI·법률·비동기 실행 통합 고정 기획서

**DESIGN FREEZE v1.0**

| 문서 상태      | 고정 기준선 / 구현 중 임의 변경 금지                                  |
|----------------|-----------------------------------------------------------------------|
| 기준 저장소    | chamgo260210/bp_new_2                                                 |
| 기준 커밋      | 967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d                              |
| 기준 일자      | 2026-08-05                                                            |
| 대상 구현 환경 | Spring Boot + FastAPI AI Server + React + PostgreSQL + Docker Compose |
| 실행 원칙      | 단계별 구현 → 검증 → 통합 → 점진 개선                                 |

이 문서는 구현 중 기능 범위와 책임 경계가 흔들리지 않도록 제품 원칙,
사용자 경험, 데이터 계약, 상태 모델, 비동기 실행, 법률·Concept 검증,
후속 분석 준비 기준을 하나의 고정 기준선으로 정의한다.

# 문서 구성

1.  0\. 문서 사용법과 변경 통제

2.  1\. 제품 비전과 서비스 정의

3.  2\. 고정 사용자 여정

4.  3\. 대화형 Idea Intake와 Opportunity Brief

5.  4\. 비동기 Job·Event·SSE 구조

6.  5\. 규제 경계(Regulatory Boundary)

7.  6\. Concept 탐색·검증·대체 생성

8.  7\. Quick Assessment·선택·상세화

9.  8\. 법률·규제 상세 보고서

10. 9\. 후속 분석 준비 상태

11. 10\. UX·UI·타이포그래피 기준

12. 11\. 데이터·API·상태·Version 계약

13. 12\. AI 책임 경계와 Prompt 원칙

14. 13\. 보안·개인정보·관측성

15. 14\. 테스트·품질 게이트·수용 기준

16. 15\. 구현 로드맵과 전환 전략

17. 부록 A. 고정 용어집

18. 부록 B. 근거 저장소와 참고 구현

# 0. 문서 사용법과 변경 통제

**고정 원칙  
**본 문서의 “고정 불변식”, 사용자 여정, 단계 책임, 상태 정의, 외부
계약은 구현 편의를 이유로 임의 변경하지 않는다. 변경 필요 시 Decision
Record를 작성하고 사용자 승인 후 v1.1 이상으로 개정한다.

## 0.1 문서의 목적

- 대화형 Idea Intake부터 Concept 선택, 법률 보고서, 분석 준비 상태까지
  하나의 제품 구조로 고정한다.

- 기존 구현을 폐기하는 것이 아니라 재사용·이관·대체 범위를 명확히 한다.

- 로컬 Codex 작업이 단계 중간에 범위를 확장하거나 계약을 약화하지
  못하도록 수용 기준을 먼저 정의한다.

- 각 단계는 독립적으로 테스트 가능하고, 다음 단계는 확정된 Version
  Snapshot만 사용한다.

## 0.2 고정 불변식

| **ID** | **불변식**                                                                                 |
|--------|--------------------------------------------------------------------------------------------|
| I-01   | 채팅은 입력 경험이며, 다음 단계의 기준은 확정된 Opportunity Brief Version이다.             |
| I-02   | 사용자 확정값, 문서 추출값, AI 제안값, 기본 가정, 결측값을 구분한다.                       |
| I-03   | 법률은 선택 후 거절하는 심판이 아니라 Concept 생성 단계의 구현 제약이다.                   |
| I-04   | 사용자에게 공개되는 Concept 3개는 Origin과 법률 구현 가능성 검사를 통과해야 한다.          |
| I-05   | 검증되지 않은 실패 Draft는 내부 기록으로만 보존하고 사용자 후보 카드로 노출하지 않는다.    |
| I-06   | 비동기 처리의 실제 단계만 표시하며 가짜 진행률과 모델의 숨겨진 추론을 표시하지 않는다.     |
| I-07   | 선택 이후 법률 단계는 신규 PASS/FAIL이 아니라 근거·이행사항·보고서 발행 단계다.            |
| I-08   | 법률 민감 필드가 변경된 경우에만 영향 범주를 증분 재검사한다.                              |
| I-09   | 후속 분석의 준비 상태를 READY / NEEDS_CONFIRMATION / MISSING으로 명시한다.                 |
| I-10   | AI 출력 계약을 약화하거나 임의 기본값으로 실패 결과를 통과시키지 않는다.                   |
| I-11   | 모든 단계는 입력 Hash, Version, 상태, Job Event로 재현 가능해야 한다.                      |
| I-12   | 프로젝트 제목·여정 단계는 기존 위계를 유지하고 본문·카드·보조문구만 1~2단계 작게 조정한다. |

## 0.3 변경 절차

19. 변경 요청은 “문제 / 현재 불변식 / 제안 변경 / 영향 범위 /
    마이그레이션 / 테스트”를 포함한 ADR로 작성한다.

20. 외부 API, DB 계약, 사용자 여정, 상태 전이가 바뀌면 설계 문서 버전을
    올린다.

21. 동일 단계 내부의 문구·스타일 미세 조정은 ADR 없이 가능하지만
    불변식은 침해할 수 없다.

22. 변경 전 기존 E2E와 새 수용 기준을 동시에 실행하고, 실패 시 기능
    플래그 또는 롤백 경로를 남긴다.

# 1. 제품 비전과 서비스 정의

**제품 정의  
**사용자가 문제나 아이디어를 대화로 설명하면 AI가 필요한 질문으로 사업
탐색 기준을 구조화하고, 공식 법률 근거로 규제 경계를 설정한 뒤, 그 경계
안에서 실제 구현 가능한 Concept 3개를 생성·검증한다. 사용자가 Concept를
선택하면 법률과 이행 조건을 보고서로 발행하고 후속 사업성 분석 입력의
준비 상태까지 연결한다.

## 1.1 이 제품이 해결하는 문제

- 사용자는 완성된 사업계획서가 없어도 시작할 수 있어야 한다.

- AI가 무엇을 이해하고 무엇을 추정했는지 사용자가 확인할 수 있어야 한다.

- 법률 검토가 뒤늦게 아이디어를 거절하지 않고, 구현 가능한 경로를 만드는
  제약으로 작동해야 한다.

- 검증되지 않은 Concept에 사용자가 시간과 애착을 쓰지 않도록 통과 후보만
  공개해야 한다.

- 시장·BM·기술운영·재무·Persona 분석에 필요한 값이 준비됐는지 정직하게
  보여줘야 한다.

## 1.2 사용자에게 보이는 큰 단계

| **아이디어 탐색** | **Concept 생성** | **비교·선택** | **법률 보고서** | **사업성 분석** |
|-------------------|------------------|---------------|-----------------|-----------------|

내부적으로는 더 많은 상태와 검증이 존재하지만, 사용자 내비게이션은 위
다섯 단계로 단순화한다.

# 2. 고정 사용자 여정

| **단계**          | **사용자 행동**                                        | **시스템 행동**                                            | **완료 조건**                  |
|-------------------|--------------------------------------------------------|------------------------------------------------------------|--------------------------------|
| 1\. 아이디어 대화 | 짧은 설명, 답변, 문서 첨부                             | 정보 추출, 후속 질문, Brief 초안 갱신                      | 필수 항목 충족                 |
| 2\. Brief 확인    | 항목 수정, LOCKED/PREFERRED/OPEN/ASSUMPTION 지정, 확정 | 확정 Version과 Hash 저장                                   | CONFIRMED                      |
| 3\. 규제 경계     | 충돌 조건 수정 또는 추가 질문 답변                     | 공식 근거 조회, Boundary Rule 생성                         | READY 또는 BLOCKED/NEEDS_INPUT |
| 4\. Concept 탐색  | 진행 보드 확인                                         | 후보 생성, Origin 검사, 법률 구현 가능성 검사, 재설계/대체 | 적격 3개 확보                  |
| 5\. 비교·선택     | Quick Assessment 확인, 1~2개 선택                      | 선택 Snapshot, 선택 이유 저장                              | SELECTED                       |
| 6\. 상세화        | 필요한 가정 확인·수정                                  | 역할·거래·데이터·운영 구조 확장                            | DETAIL_CONFIRMED               |
| 7\. 법률 보고서   | 체크리스트, 질문, 수정안, 발행                         | 기존 검증근거로 보고서·증분 검사·Snapshot 발행             | PUBLISHED                      |
| 8\. 분석 준비     | 누락값과 확인 필요값 보완                              | 분석별 READY/NEEDS_CONFIRMATION/MISSING 계산               | 분석별 Gate 충족               |

## 2.1 Stale 규칙

- 확정 Brief가 변경되면 규제 경계, Concept Batch, Quick Assessment, 선택
  결과는 Stale 후보가 된다.

- 규제 경계가 변경되면 Concept 이후 산출물이 Stale 후보가 된다.

- 선택 Concept의 법률 민감 필드만 변경된 경우 전체 흐름이 아니라 영향
  법률 범주만 재검사한다.

- Stale 결과는 삭제하지 않고 이력으로 남기되 현재 결과로 표시하지
  않는다.

# 3. 대화형 Idea Intake와 Opportunity Brief

## 3.1 화면 구조

| **영역**          | **Desktop**                             | **Mobile**          |
|-------------------|-----------------------------------------|---------------------|
| 상단              | 프로젝트 정보와 여정 단계 고정          | 동일                |
| 대화 영역         | 좌측 65%: 메시지, 질문, 파일, 작업 상태 | 기본 화면           |
| Brief 정보판      | 우측 35%: 현재 추출값, 상태, 출처, 확정 | Drawer/Bottom sheet |
| 규제 경계 확정 후 | 좌측 30% 요약 + 우측 70% Concept 보드   | 탭 또는 순차 화면   |

## 3.2 필수 정보

| **Field Key**          | **표시명**       | **필수** | **설명**                             |
|------------------------|------------------|----------|--------------------------------------|
| problem_or_opportunity | 문제 또는 기회   | 필수     | 무엇이 불편하거나 개선되어야 하는가  |
| primary_actor          | 주요 사용자/고객 | 필수     | 누가 문제를 겪고 사용하는가          |
| beneficiaries          | 수혜자           | 선택     | 구매자·운영자·공공 수혜자 등         |
| usage_context          | 사용 상황        | 필수     | 언제, 어디서, 어떤 맥락인가          |
| desired_outcome        | 원하는 결과      | 필수     | 무엇이 달라져야 하는가               |
| target_region          | 대상 국가·지역   | 필수     | 법률·시장 범위                       |
| locked_constraints     | 반드시 유지      | 필수     | 변경 금지 조건                       |
| open_decisions         | 열린 결정        | 필수     | Concept가 제안할 수 있는 공간        |
| prohibited_preferences | 원하지 않는 방식 | 선택     | 사용자 금지 조건                     |
| role_scope             | 사업 역할 범위   | 필수     | 직접 판매·중개·정보·운영 가능 범위   |
| regulated_activities   | 규제 민감 활동   | 필수     | 개인정보·결제·배송·수거·의료·금융 등 |

## 3.3 품질 향상 정보

- 현재 대안과 기존 방식의 한계

- 보유 자원·기술·파트너·데이터·채널

- 기간·예산·인력 제약

- 선호 채널과 수익 의도

- 성공 기준과 초기 검증 가설

- 차별화 가능한 자산

## 3.4 조건부 질문군

| **Trigger**     | **후속 질문군**                                                                |
|-----------------|--------------------------------------------------------------------------------|
| 개인정보·데이터 | 수집 항목, 수집·보관 주체, 보관 기간, 위치·건강·생체·아동 정보, 자동 판단 여부 |
| 결제·거래       | 판매자, 결제 수취자, 수수료, 환불·분쟁 책임, 정기결제                          |
| 물리적 활동     | 배송·수거·운반·보관 주체, 직접/파트너, 허가·자격, 소유권 이전                  |
| 의료·건강       | 진단·치료·예방 주장, 건강정보, 전문가 대체, 웰니스/의료 경계                   |
| 금융            | 투자·대출·보험·신용 추천, 자금 보관·이체, 수익률 주장                          |
| 미성년자        | 보호자 동의, 결제, 개인정보                                                    |
| 콘텐츠·IP       | 업로드 콘텐츠, 타인 저작물, AI 생성물 상업 이용, 권리 조건                     |

## 3.5 Field Decision Status

| **상태**   | **의미**           | **Concept 처리**       | **표시**       |
|------------|--------------------|------------------------|----------------|
| LOCKED     | 반드시 유지        | 변경 금지, Trace 필수  | 잠금 아이콘    |
| PREFERRED  | 선호하나 대안 가능 | 변경 시 이유·비교 표시 | 선호 배지      |
| OPEN       | 미결정             | 후보별 변형 허용       | 열린 결정 배지 |
| ASSUMPTION | 검증되지 않은 가정 | 평가·실험 과제로 전달  | 가정 경고      |

## 3.6 Source Provenance

| **Source Type**    | **의미**           |
|--------------------|--------------------|
| USER_CONFIRMED     | 사용자가 확인·확정 |
| SOURCE_EXTRACTED   | 문서에서 추출      |
| AI_PROPOSED        | AI 제안            |
| DEFAULT_ASSUMPTION | 명시된 기본 가정   |
| MISSING            | 결측               |

## 3.7 대화 규칙

- 한 번에 2~4개 질문만 제시한다.

- 이미 답한 질문을 표현만 바꿔 반복하지 않는다.

- 답변이 모순되면 조용히 덮어쓰지 않고 충돌을 보여준다.

- 질문의 목적과 다음 단계에 필요한 이유를 짧게 설명한다.

- AI는 장문의 사업계획을 강요하지 않고 OPEN 항목을 허용한다.

- 사용자는 채팅 답변 또는 문서 첨부로 여러 질문에 한꺼번에 답할 수 있다.

## 3.8 종료 조건

| **READY_FOR_CONFIRMATION** | **NEEDS_INPUT**                   |
|----------------------------|-----------------------------------|
| 문제·대상·결과·지역 존재   | 사용자와 구매자 구분 불가         |
| LOCKED와 OPEN 구분         | 중요 역할·거래 구조가 전혀 불명확 |
| 규제 민감 활동 식별        | 지역 미정                         |
| 명백한 모순 없음           | 핵심 목표 충돌                    |
| Concept 변형 공간 존재     | 모든 요소가 LOCKED                |
|                            | 명백한 금지 활동이 LOCKED         |

# 4. 비동기 Job·Event·SSE 구조

| **요청** | **Job 생성** | **Worker 실행** | **Event 저장** | **SSE 전송** | **UI 복원** |
|----------|--------------|-----------------|----------------|--------------|-------------|

## 4.1 원칙

- POST 요청은 장기 작업 완료를 기다리지 않고 Job ID와 현재 상태를
  반환한다.

- 모든 작업 단계 이벤트는 DB에 저장하여 새로고침·재로그인 후 복원한다.

- SSE를 기본으로 하고 연결 실패 시 동일 Event Cursor 기반 Polling으로
  전환한다.

- 진행률 퍼센트는 실제 계산 근거가 있을 때만 사용하고, 기본은 단계 완료
  상태를 표시한다.

- 사용자 메시지와 운영 진단 로그를 분리한다.

## 4.2 Event 계약

| **필드**      | **설명**                                    |
|---------------|---------------------------------------------|
| eventId       | 단조 증가 ID 또는 UUID                      |
| jobId         | Job 식별자                                  |
| projectId     | 프로젝트                                    |
| stage         | 업무 단계                                   |
| eventType     | 기계 판독 이벤트                            |
| status        | QUEUED/RUNNING/COMPLETED/FAILED/NEEDS_INPUT |
| messageKey    | 사용자 문구 Key                             |
| messageParams | 안전한 치환값                               |
| technicalCode | 운영용 오류 코드                            |
| sequence      | 재생 순서                                   |
| occurredAt    | 발생 시각                                   |

## 4.3 Event 예시

| **Stage**           | **Event Type**                 | **사용자 표시**                                        |
|---------------------|--------------------------------|--------------------------------------------------------|
| IDEA_INTAKE         | FILE_EXTRACTION_STARTED        | 첨부 문서의 텍스트와 표를 읽고 있습니다.               |
| IDEA_INTAKE         | FOLLOWUP_QUESTIONS_READY       | 추가 확인이 필요한 질문을 준비했습니다.                |
| REGULATORY_BOUNDARY | OFFICIAL_SOURCE_LOOKUP_STARTED | 관련 공식 법령 근거를 확인하고 있습니다.               |
| CONCEPT_EXPLORATION | SLOT_LEGAL_VALIDATION_STARTED  | Concept의 사업자 역할과 운영 구조를 확인하고 있습니다. |
| CONCEPT_EXPLORATION | SLOT_REDESIGN_STARTED          | 규제 조건에 맞도록 운영 방식을 다시 설계하고 있습니다. |
| LEGAL_REPORT        | REPORT_BUILD_STARTED           | 선택한 Concept의 법률 보고서를 구성하고 있습니다.      |

## 4.4 SSE 계약

**권장 Endpoint  
**GET /api/v2/jobs/{jobId}/events · Content-Type: text/event-stream ·
Last-Event-ID와 cursor를 모두 지원한다. 연결 종료 후 Polling은 GET
/api/v2/jobs/{jobId}/events?after={sequence}를 사용한다.

# 5. 규제 경계(Regulatory Boundary)

**역할 정의  
**규제 경계는 최종 법률자문이나 적법성 보증이 아니다. Concept 탐색에서
금지할 역할·활동, 허용 가능한 구현 패턴, 필수 파트너·통제·고지, 미해결
사실을 공식 근거와 연결하는 생성 제약이다.

## 5.1 Boundary Rule 유형

| **Rule Type**       | **설명**                   | **예**                       |
|---------------------|----------------------------|------------------------------|
| PROHIBITED_ROLE     | 맡으면 안 되는 사업자 역할 | 무허가 직접 수거 사업자      |
| PROHIBITED_ACTIVITY | 금지·제외 활동             | 확인되지 않은 의료 효능 단정 |
| ALLOWED_PATTERN     | 허용 가능한 구조           | 허가 사업자 제휴             |
| REQUIRED_CONTROL    | 필수 운영 통제             | 접근권한·보관기간·감사로그   |
| REQUIRED_PARTNER    | 필요 자격·파트너           | 허가된 운반 사업자           |
| REQUIRED_DISCLOSURE | 필수 고지·동의             | 위치정보 이용 고지           |
| UNRESOLVED_FACT     | 추가 확인 전 판단 불가     | 실제 결제 수취 주체          |

## 5.2 Boundary 상태

| **상태**    | **의미**                | **다음 행동**               |
|-------------|-------------------------|-----------------------------|
| READY       | 탐색 가능               | Concept 생성                |
| NEEDS_INPUT | 사실 부족               | 대화 질문                   |
| BLOCKED     | LOCKED 조건과 경계 충돌 | 수정안 선택 또는 Brief 변경 |
| FAILED      | 기술/근거 조회 실패     | 재시도 또는 운영 확인       |

## 5.3 사용자 피드백

- 탐색 가능한 구현 방향

- 피해야 할 구현 방향

- 필수 파트너·허가·고지

- 추가로 확인할 사실

- 어느 LOCKED 조건이 충돌하는지

- 어떻게 바꾸면 탐색 가능한지

## 5.4 법률 Evidence와 Boundary Rule 분리

| **Evidence**                     | **Boundary Rule**                      |
|----------------------------------|----------------------------------------|
| 법률명·조문·제목·발췌·시행일·URL | Concept 생성에 직접 적용할 사업상 규칙 |
| 공식 근거 보존                   | 실행 가능한 문장                       |
| 변경하지 않음                    | 역할·활동·통제 단위로 정규화           |

# 6. Concept 탐색·검증·대체 생성

| **Slot 생성** | **Concept 골격 생성** | **Origin 검사** | **법률 구현 검사** | **재설계/대체** | **적격 3개** |
|---------------|-----------------------|-----------------|--------------------|-----------------|--------------|

## 6.1 Concept 최소 구현 골격

| **영역**          | **필수 내용**                                   |
|-------------------|-------------------------------------------------|
| Identity          | 이름, 한 줄 설명                                |
| Customer          | 핵심 고객, 사용 상황, 수혜자                    |
| Mechanism         | 문제 해결 메커니즘, 가치 제안, 차별화           |
| Business          | 수익 가설, 채널 가설                            |
| Roles             | 플랫폼·판매자·파트너 역할                       |
| Transaction       | 주문·결제·환불·소유권 흐름                      |
| Data              | 수집·처리·보관 주체와 목적                      |
| Operations        | 물리적 활동, 파트너, 큰 운영 방식               |
| Validation        | 가정, 위험, 초기 검증 포인트                    |
| Legal Feasibility | 구현 상태, 필수 통제, 피해야 할 변형, 근거 연결 |

## 6.2 후보 상태

| **상태**          | **의미**                | **사용자 공개**  |
|-------------------|-------------------------|------------------|
| QUEUED            | 대기                    | 작업 Slot만 표시 |
| GENERATING        | 생성 중                 | 상세 숨김        |
| VALIDATING_ORIGIN | 고정값·기준 검사        | 상세 숨김        |
| VALIDATING_LEGAL  | 구현 가능성 검사        | 상세 숨김        |
| REDESIGNING       | 수정 가능한 충돌 재설계 | 상세 숨김        |
| READY             | 적격 후보               | 공개             |
| NEEDS_INPUT       | 사용자 사실 필요        | 질문 표시        |
| FAILED            | 기술적/복구 불가        | 재시도 표시      |

## 6.3 법률 구현 가능성 상태

| **상태**                    | **설명**                   | **처리**         |
|-----------------------------|----------------------------|------------------|
| IMPLEMENTABLE               | 현재 구조로 구현 경로 확인 | 적격             |
| IMPLEMENTABLE_WITH_CONTROLS | 필수 통제를 반영하면 가능  | 적격             |
| REDESIGN_REQUIRED           | 역할·운영 구조 수정 필요   | 재설계 후 재검사 |
| INSUFFICIENT_INFORMATION    | 사실 부족                  | 질문 또는 제외   |
| HARD_BLOCK                  | 핵심 구조 충돌             | 폐기             |

## 6.4 생성·검증 책임 분리

| **구성요소**                | **책임**                                    |
|-----------------------------|---------------------------------------------|
| Concept Generator           | 사업 골격과 법률 대응 가설 생성             |
| Origin Validator            | LOCKED/Brief 일관성 결정론 검사             |
| Legal Feasibility Validator | Boundary Rule과 구현 구조의 적합성 판단     |
| Orchestrator                | Slot 격리, 재설계/대체, 적격 수 집계        |
| Persistence                 | 실패 Draft와 이유를 내부 기록, READY만 공개 |

## 6.5 Slot 실패 격리

- 한 Slot의 Timeout이 다른 Slot의 Schema Repair나 법률검사를 막지
  않는다.

- 각 Slot은 VALID / SCHEMA_INVALID / TRANSIENT_PROVIDER_FAILURE /
  PERMANENT_PROVIDER_FAILURE로 독립 분류한다.

- 정상 Slot은 재호출하지 않는다.

- Slot별 초기 호출 후 Schema Repair 또는 Transient Retry 중 하나만
  허용한다.

- 사용자에게는 기술 코드 대신 단계와 복구 가능 여부를 표시한다.

## 6.6 Trace 원칙

**결정론적 Trace  
**AI가 시스템이 이미 알고 있는 sourceValue·법률 원문을 재복사해 증명하게
하지 않는다. 구조 Key와 원본 값은 시스템이 조립하고, AI는 후보의 대응
값이나 구현 설명만 반환한다. 최종 Trace는 Validator가 생성한다.

## 6.7 적격 3개 정책

- 사용자에게 공개할 목표 수는 3개로 고정한다.

- 내부적으로 실패한 수만큼 대체 생성하며 총 검사 한도와 Round 한도는
  정책값으로 유지한다.

- 3개를 확보하지 못하면 FAILED가 아니라 원인에 따라 NEEDS_INPUT 또는
  RETRYABLE_FAILED로 분리한다.

- 공개 후보 3개 외에 적격 Reserve 후보를 최대 1~2개 보관할 수 있으나
  초기 UI에는 표시하지 않는다.

# 7. Quick Assessment·선택·상세화

## 7.1 평가 기준

| **영역**              | **설명**           |
|-----------------------|--------------------|
| problemFit            | 문제 적합성        |
| customerValue         | 고객 가치          |
| differentiation       | 차별성             |
| executionFeasibility  | 실행 가능성        |
| revenuePotential      | 수익 가능성        |
| testability           | 초기 검증 용이성   |
| legalFeasibility      | 법률 구현 가능성   |
| complianceComplexity  | 준수 복잡도        |
| partnerDependency     | 필수 파트너 의존도 |
| assumptionUncertainty | 가정 불확실성      |

## 7.2 선택 원칙

- Quick Assessment는 상대 비교 근거이며 자동 선택하지 않는다.

- 사용자는 1개 또는 최대 2개를 선택하고 선택 이유를 저장한다.

- 선택 Snapshot은 Concept Version과 법률 프로필 Version을 함께 고정한다.

## 7.3 상세화 항목

- 기능 구성과 사용자 흐름

- 파트너 역할과 책임

- 주문·결제·환불·소유권 구조

- 데이터 수집·보관·삭제 구조

- 운영 프로세스와 채널

- 가격·수익·비용 가설

- 핵심 지표

- 초기 실험 계획

## 7.4 법률 민감 변경 감지

| **변경 Field**          | **영향 예**          |
|-------------------------|----------------------|
| businessRole            | 판매자/중개자 책임   |
| paymentActor            | 약관·소비자·전자금융 |
| deliveryCollectionActor | 인허가·물리적 책임   |
| dataPurposeRetention    | 개인정보·위치정보    |
| targetRegion            | 관할 법률            |
| regulatedProductType    | 산업별 규제          |
| advertisingClaims       | 표시광고·의료 주장   |

# 8. 법률·규제 상세 보고서

**보고서 역할  
**새로운 합격·불합격을 만드는 단계가 아니다. Concept 생성 때 이미 확인한
법률 구현 가능성, 공식 근거, 필수 통제, 피해야 할 변형, 사업 개시 전
이행사항을 선택 Concept Snapshot 기준으로 설명·발행한다.

## 8.1 보고서 구성

23. 검토 목적과 범위

24. 검토의 전제가 된 Concept 구조

25. 종합 규제 상태

26. 관련 법률 범주

27. 법률별 적용 근거

28. 구현 시 지켜야 할 조건

29. 사업 개시 전 체크리스트

30. 조건부 의무

31. 추가 확인이 필요한 사항

32. 전문가 확인 권고

33. 별첨 법령·조문

34. 책임의 한계

## 8.2 5단 판단 사슬

| **Concept의 어떤 사실** | **규제 영역** | **발생 의무** | **위반 시 결과** | **필수 조치** |
|-------------------------|---------------|---------------|------------------|---------------|

## 8.3 사용자 행동

- 체크리스트 완료 표시

- 추가 질문에 사실과 출처 답변

- 수정안 선택

- Concept 새 Version 생성

- 영향 범주만 증분 재검사

- 정식 보고서 Snapshot 발행

## 8.4 수렴과 발행

- 미해결 수정 요청과 미답변 필수 질문이 없으면 CONVERGED.

- 미완료 운영 체크리스트는 발행을 막지 않고 “이행 예정 사항”으로 수록할
  수 있다.

- 발행 이후 Concept가 변경돼도 기존 보고서 Snapshot은 불변으로 보존한다.

# 9. 후속 분석 준비 상태

| **상태**           | **의미**                                       |
|--------------------|------------------------------------------------|
| READY              | 필수 구조와 확정값이 준비됨                    |
| NEEDS_CONFIRMATION | AI 제안·문서 추출·기본 가정의 사용자 확인 필요 |
| MISSING            | 필수 값 없음                                   |
| BLOCKED            | 선행 결과 미완료 또는 Stale                    |

## 9.1 분석별 예시

| **분석**         | **필수 입력 예**                   | **준비 판정**    |
|------------------|------------------------------------|------------------|
| Quick Assessment | 검증된 Concept 3개                 | READY            |
| 시장 분석        | 고객·지역·문제·대안·시장 가정      | 확인 여부에 따라 |
| BM 분석          | 가치·수익·채널·파트너·비용 구조    | 확인 여부에 따라 |
| 기술·운영        | 기능·데이터·역할·운영 프로세스     | 확인 여부에 따라 |
| 재무             | 객단가·수량·변동비·고정비·초기투자 | 결측이면 MISSING |
| Persona          | 핵심 고객·상황·욕구                | READY 가능       |
| 사용자 검증      | 핵심 가정·검증 질문·성공 기준      | READY 가능       |

## 9.2 값 표시

**정직한 준비 상태  
**“모든 값이 생성되었습니다”라고 과장하지 않는다. 각 값 옆에
USER_CONFIRMED / SOURCE_EXTRACTED / AI_PROPOSED / DEFAULT_ASSUMPTION /
MISSING을 표시한다. 재무처럼 결정론 계산이 필요한 단계는 사용자 확정 전
공식 결과를 만들지 않는다.

# 10. UX·UI·타이포그래피 기준

## 10.1 시각적 위계

| **요소**       | **권장 Desktop** | **권장 Mobile** | **비고**              |
|----------------|------------------|-----------------|-----------------------|
| 프로젝트 제목  | 24–28px          | 22–24px         | 기존 위계 유지        |
| 여정 단계 제목 | 18–20px          | 17–19px         | 기존 위계 유지        |
| 본문 섹션 제목 | 15–16px          | 14–15px         | 현재보다 1단계 축소   |
| 카드 제목      | 14–15px          | 13–14px         | 굵기 600              |
| 본문           | 13–14px          | 13px            | 현재보다 1~2단계 축소 |
| 보조문구·메타  | 11.5–12.5px      | 11.5–12px       | 가독성 하한 유지      |
| 상태 배지      | 11.5–12px        | 11.5px          | 짧은 문구             |

**폰트 축소 적용 범위  
**프로젝트명, 주요 페이지 제목, 전체 Journey Stepper는 축소하지 않는다.
채팅 메시지, 카드 본문, 도움말, 법률 Evidence, Timeline, 메타데이터의
크기와 간격을 줄여 정보 밀도를 높인다.

## 10.2 밀도와 간격

- 본문 line-height 1.45~1.55, 메타 1.35~1.45.

- 카드 내부 기본 padding 16px, 조밀 카드 12px.

- 섹션 간격 24px, 카드 간격 12~16px.

- 긴 법률 원문은 접힘 요소로 두고 쉬운 설명을 먼저 표시.

- 상태 Timeline은 한 줄 메시지 + 필요 시 펼쳐보기.

## 10.3 채팅 UX

- 메시지 폭은 화면 전체를 차지하지 않고 최대 폭을 제한한다.

- AI 질문은 번호·선택지·짧은 이유를 포함한다.

- 파일 메시지는 업로드/파싱/추출 상태를 하나의 카드로 갱신한다.

- Brief 정보판은 변경된 필드에만 짧은 강조 표시.

- 사용자 승인 버튼은 대화 하단과 정보판 하단에 중복 배치 가능하나 동일
  Action을 호출한다.

## 10.4 Concept 작업 보드

- Slot은 고객경험·운영파트너·수익채널 등의 탐색 초점을 표시한다.

- 완성 전 Concept 상세와 이름은 숨긴다.

- 현재 단계, 완료 단계, 재설계 이유의 사용자 친화 메시지만 표시한다.

- READY 후 카드가 확장되며 법률 구현 방식과 필수 통제를 함께 표시한다.

# 11. 데이터·API·상태·Version 계약

## 11.1 권장 테이블 그룹

| **도메인**   | **테이블**                                                                                                                         |
|--------------|------------------------------------------------------------------------------------------------------------------------------------|
| Conversation | idea_conversations, idea_messages, idea_attachments                                                                                |
| Async        | jobs 또는 task_runs 확장, job_events                                                                                               |
| Brief        | opportunity_brief_versions, opportunity_field_values                                                                               |
| Boundary     | regulatory_boundary_runs, regulatory_boundary_versions, boundary_rules, boundary_evidence, boundary_questions                      |
| Concept      | concept_exploration_batches, concept_slots, concept_attempts, concept_validation_results, concept_versions, concept_legal_profiles |
| Selection    | concept_selection_versions, concept_detail_versions                                                                                |
| Report       | legal_report_versions, publications                                                                                                |
| Readiness    | analysis_readiness_snapshots                                                                                                       |

## 11.2 API 그룹

| **그룹**     | **Endpoint 예**                                                                |
|--------------|--------------------------------------------------------------------------------|
| Conversation | POST /idea-conversations; POST /messages; POST /attachments; GET /conversation |
| Brief        | GET /opportunity-brief/current; PUT /fields/{key}; POST /confirm               |
| Boundary     | POST /regulatory-boundaries; GET /current                                      |
| Concept      | POST /concept-explorations; GET /current; GET /slots; GET /events              |
| Selection    | POST /concept-selections; POST /selected-concepts/{id}/enrich                  |
| Report       | POST /selected-concepts/{id}/legal-reports; GET /publications/latest           |
| Readiness    | GET /analysis-readiness                                                        |
| Events       | GET /jobs/{jobId}/events                                                       |

## 11.3 Version 연결

| **Conversation** | **Brief Version** | **Boundary Version** | **Concept Version** | **Selection Version** | **Detail Version** | **Legal Report Version** | **Readiness Snapshot** |
|------------------|-------------------|----------------------|---------------------|-----------------------|--------------------|--------------------------|------------------------|

## 11.4 Idempotency·Hash

- 확정 Snapshot은 canonical JSON과 SHA-256 Hash를 갖는다.

- 동일 입력·동일 단계·동일 정책 Version의 중복 실행을 차단한다.

- 실행 중 Job은 재사용하고, 명시적 재실행은 새 Attempt 또는 새 Run
  정책을 따른다.

- 응답은 원 TaskRun/Attempt/Hash와 일치해야 채택한다.

# 12. AI 책임 경계와 Prompt 원칙

| **AI가 수행**                  | **시스템이 수행**                |
|--------------------------------|----------------------------------|
| 대화 의미 추출, 후속 질문 제안 | Version·Hash·상태·중복 실행      |
| OPEN 영역의 Concept 생성       | LOCKED 원본값 조립과 비교        |
| 규제 Route/관련성 분류         | 공식 법령 조회와 Evidence 보존   |
| 법률 대응 구현 설명            | Boundary Rule ID·원문·Trace 조립 |
| 서술·요약·권고안               | 결정론 계산·수용 여부·공개 Gate  |

## 12.1 Structured Output

- 가능한 Provider에서는 JSON Schema Structured Output을 사용한다.

- Pydantic/Java 계약을 동일 Schema Version으로 관리한다.

- Extra field를 금지하되, 시스템이 알고 있는 값을 AI에 재복사시키지
  않는다.

- Repair는 국소 오류에만 1회, 네트워크 재시도와 Schema Repair를 중첩해
  무한화하지 않는다.

- Prompt 전체와 Raw 사용자 Idea를 경고 로그에 남기지 않는다.

## 12.2 Prompt Version

- Idea Intake, Boundary Routing, Boundary Normalization, Concept
  Generation, Concept Legal Validation, Quick Assessment, Enrichment,
  Legal Report를 별도 Prompt Version으로 관리한다.

- Prompt 변경은 Fixture와 Golden Test를 동반한다.

- 모델 교체 시 동일 Contract Fixture와 실제 샘플 E2E를 재검증한다.

# 13. 보안·개인정보·관측성

- 첨부파일은 허용 형식·크기·MIME·확장자를 모두 검사하고 악성 파일 검사를
  위한 Hook을 둔다.

- 원본 파일, 추출 텍스트, AI 전송 Snapshot의 보관 정책을 분리한다.

- API Key, Authorization, 내부 Token, 전체 Prompt, Raw Provider Body는
  사용자 Event나 일반 로그에 기록하지 않는다.

- 사용자 메시지·첨부·확정 사실의 삭제 정책과 파생 산출물 무효화 정책을
  정의한다.

- 법률 보고서는 AI 사전점검이며 법률자문·적법성 확정이 아니라는 한계를
  명시한다.

- Job 메트릭은 단계별 지연, Provider 코드, Repair/Retry 수, Slot 성공률,
  Stale 수를 집계한다.

# 14. 테스트·품질 게이트·수용 기준

## 14.1 테스트 계층

| **계층**            | **주요 테스트**                                        |
|---------------------|--------------------------------------------------------|
| Domain Unit         | 상태 전이, Version, Stale, Field Status, Boundary Rule |
| AI Contract         | Schema, Extra field, Prompt Fixture, Trace 조립        |
| Backend Integration | DB Migration, API Envelope, Idempotency, SSE Replay    |
| Frontend Unit       | ViewModel, 상태 카드, Drawer, Event reducer            |
| Component           | 채팅, 파일 카드, Brief 편집, Concept Slot, 법률 보고서 |
| E2E                 | 대화→Brief→Boundary→3 Concept→선택→보고서→Readiness    |
| Resilience          | Provider Timeout, 한 Slot 실패, SSE 재접속, 중복 클릭  |
| Security            | 권한, 파일 검증, Secret 비노출, 프로젝트 격리          |
| Accessibility       | 키보드, ARIA live, heading, focus, contrast            |

## 14.2 필수 E2E 시나리오

35. 짧은 Idea 입력 → 3회 이하 질문 → Brief 확정 → Boundary READY →
    Concept 3개 → 선택 → 보고서 발행.

36. 문서 첨부 → 추출값 확인 → 누락 질문 답변 → Brief 확정.

37. LOCKED 조건이 규제 경계와 충돌 → BLOCKED → 수정안 선택 → 새 Brief
    Version → READY.

38. Concept Slot 하나 Timeout, 둘 Schema 오류 → Slot별 독립 복구 → 다른
    Slot 작업 유지.

39. 후보가 REDESIGN_REQUIRED → 운영 역할 수정 →
    IMPLEMENTABLE_WITH_CONTROLS.

40. Brief 변경 → 기존 Boundary/Concept/Assessment가 Stale.

41. 선택 Concept의 결제 주체 변경 → 영향 법률 범주만 증분 재검사.

42. 재무 필수값 누락 → Readiness MISSING, 기본값으로 공식 완료 처리
    금지.

43. SSE 연결 중단 → Last-Event-ID 이후 복원, 중복 이벤트 없이 UI 재구성.

## 14.3 완료 정의

- 테스트 통과만이 아니라 실제 Docker 환경에서 UI와 Event 흐름을
  검증한다.

- 새 기능은 문서·Migration·API Contract·Fixture·UI 상태를 함께 제공한다.

- 기존 공식 Journey를 회귀시키지 않는다.

- 새 화면은 본문 폰트 축소와 정보 밀도 기준을 통과한다.

- 운영 로그와 사용자 메시지가 분리됐음을 테스트한다.

# 15. 구현 로드맵과 전환 전략

| **Phase**                 | **핵심 결과**                                   | **선행** |
|---------------------------|-------------------------------------------------|----------|
| 0\. Baseline Freeze       | 현행 감사, Feature Flag, ADR, 계약 동결         | 없음     |
| 1\. Domain Foundation     | Conversation/Brief/Boundary/Event 데이터와 상태 | 0        |
| 2\. Async Event           | Job Event, SSE, Polling fallback                | 1        |
| 3\. Conversational Intake | 채팅·파일·질문·Brief 확인                       | 1,2      |
| 4\. Regulatory Boundary   | Evidence→Rule 정규화, 충돌·질문                 | 3        |
| 5\. Concept Core          | Slot 격리, 결정론 Trace, 구현 가능성 상태       | 4        |
| 6\. Concept Workboard     | 비동기 Slot UI, READY 3개 공개                  | 5        |
| 7\. Quick Assessment      | 신규 Concept 계약 평가·선택                     | 6        |
| 8\. Enrichment            | 선택 Concept 상세화·민감 변경 감지              | 7        |
| 9\. Legal Report          | 5단 사슬, 체크리스트, 증분검사, 발행            | 8        |
| 10\. Readiness            | 분석별 준비 상태·확정 입력                      | 9        |
| 11\. E2E Cutover          | 기존 공식 Journey 전환, 레거시 보존             | 0~10     |

## 15.1 전환 원칙

- 기존 \`/idea\`, \`/legal\`, \`/journey/concept\` 동작을 즉시 삭제하지
  않고 Feature Flag 또는 Versioned route로 병행한다.

- 기존 Idea Source·Origin·Legal Evidence·Concept Draft 테이블의 재사용
  가능성을 우선 평가한다.

- 새 테이블은 additive migration으로 만들고, 기존 결과를 무리하게
  변환하지 않는다.

- 새 Journey E2E가 Green이고 데이터 복원·Stale 규칙이 검증된 뒤 공식
  Route를 전환한다.

- 레거시 MVP Quick/Detailed/Persona 등은 유지하되 신규
  Selection/Readiness 계약에 맞춰 연결한다.

## 15.2 작업 중 금지

- 한 단계 작업에서 다음 단계 구현까지 확장

- 테스트를 맞추기 위한 Contract 완화

- 실패 후보 사용자 노출

- 법률 Evidence를 Guardrail 문장으로 그대로 사용

- Provider Timeout 단순 증가만으로 해결

- Raw DB/Prompt를 UI Event로 노출

- 모든 AI 제안값을 확정값으로 승격

- 폰트 축소를 프로젝트 제목·주요 단계 제목에 적용

# 부록 A. 고정 용어집

| **용어**                  | **정의**                                       |
|---------------------------|------------------------------------------------|
| Idea Conversation         | 사용자·AI 대화와 첨부 기록                     |
| Opportunity Brief         | 확정된 사업 탐색 기준 Snapshot                 |
| Regulatory Boundary       | Concept 생성용 규제 역할·활동·통제 규칙        |
| Concept Skeleton          | 비교와 법률 판단에 필요한 최소 구현 골격       |
| Legal Feasibility Profile | Concept별 구현 가능 상태·조건·근거             |
| Concept Detail            | 선택 후 확장된 역할·거래·데이터·운영·재무 가설 |
| Analysis Readiness        | 후속 분석 입력의 준비 상태 Snapshot            |
| Stale                     | 현재 상위 Version과 불일치하는 이전 결과       |
| Publication               | 발행 시점에 고정되는 법률 보고서 Snapshot      |

# 부록 B. 근거 저장소와 참고 구현

- 현재 기준 저장소: https://github.com/chamgo260210/bp_new_2.git

- 기준 커밋: 967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d (Concept
  single-candidate fan-out 포함)

- 참고 법률 UX 저장소:
  https://github.com/junwoooooooo/aivle_big_project.git · junwoo branch

- 참고 기능: 10개 법률 범주, 5단 판단 사슬, 쉬운 설명, 수정 요청,
  질문·확정 사실, 증분 재검토, 정식 보고서 발행, 타당성 3묶음, 재무 가정
  확정 원칙

- 본 문서의 추천 구조는 위 구현을 그대로 복제하지 않고 현재 프로젝트의
  Concept Eligibility와 결합한 고정 목표 구조다.
