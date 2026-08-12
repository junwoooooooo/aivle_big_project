# 09. 의사결정 기록

- 원천: `docs/governance/DECISION_LOG.md` (DEC-001~DEC-019) · `docs/product/OPEN_DECISIONS.md` (OD-001~009)
- 원문서에 이미 **`결정 / Rationale / Alternatives / 영향 문서 / 코드 영향 / 테스트 영향`** 열이
  전부 있다. **대안 열이 있다는 것이 이 자산의 값어치다** — 그대로 슬라이드가 된다.

> ⚠ **주의: 원문서 상태가 `TARGET_CANONICAL`이고 일부 결정은 이미 뒤집혔다.**
> 아래 표의 「현재 유효」 열을 반드시 확인하고 발표할 것. 죽은 결정을 실적처럼 말하면 안 된다.

---

## 1. 발표에 쓸 결정 6건 (전부 현재 유효)

### DEC-001 · DEC-002 — 데이터 소유권을 한 경계에 몰았다 ⭐

| | |
|---|---|
| **결정** | Spring이 RDB와 Object Storage를 전담한다. **AI 서버의 DB·Storage·presigned URL·로컬 저장을 금지한다** |
| **왜** | 인증·소유권·트랜잭션·아티팩트 무결성을 한 경계에서 보장. 업무 상태와 데이터 소유권의 회귀 방지 |
| **기각한 대안** | presigned GET/PUT · shared volume · 혼합 소유 |
| **코드에 남은 것** | AI 서버에 **DB 드라이버와 S3 SDK 자체가 없다.** 결과 JSON에서 `storageUrl`·`presignedUrl`·`credential` 등을 발견하면 재귀 순회로 거부 |
| **현재 유효** | ✅ |

> 발표 한 줄: **"금지를 문서로 약속하지 않고, 라이브러리를 안 넣는 것으로 막았다."**

### DEC-003 · DEC-017 — TaskRun과 TaskAttempt를 나눴다 ⭐

| | |
|---|---|
| **결정** | `TaskRun`(요청과 최종 상태) / `TaskAttempt`(실행 1회, retry·timeout·오류)를 분리. **외부 AI 호출 동안 DB 트랜잭션을 유지하지 않는다.** claim/lease, 멱등키, 입력 스냅샷/해시를 보존 |
| **왜** | 긴 외부 호출과 DB 트랜잭션을 분리하고, **retry·네트워크 모호성·중복 채택**을 추적 가능하게 관리 |
| **기각한 대안** | 단일 aggregate 트랜잭션 · 외부 호출 중 트랜잭션 유지 · outbox-only · polling-only |
| **코드에 남은 것** | `isActualTransactionActive()`를 확인해 켜져 있으면 예외를 던진다. 채택은 3조건 확인 후 **정확히 한 번** |
| **현재 유효** | ✅ |

### DEC-012 — 큰 데이터를 어떻게 넘길 것인가

| | |
|---|---|
| **결정** | **bounded inline JSON**이 기본. Spring이 크기 제한과 chunk 배열을 구성하고 초과 시 `PAYLOAD_TOO_LARGE`. Storage/presigned URL과 임시 공유 저장소는 **금지** |
| **왜** | 초기 계약을 단순하고 **검증 가능하게** 유지하면서 데이터 소유권을 지킨다 |
| **기각한 대안** | streaming-first · shared temporary channel · presigned GET/PUT · unbounded inline |
| **코드에 남은 것** | 2 MiB 제한, textContents 1–64, chunk 총합 64, chunk 텍스트 16,384자 |
| **현재 유효** | ✅ |

> PIILOT의 Redis 5장(133MB Base64 전송 문제)과 **같은 종류의 문제를 다르게 푼 사례**다.
> 나란히 놓으면 비교 설명이 쉽다.

### DEC-009 · DEC-019 — 법을 모델이 지어내지 않게 했다

| | |
|---|---|
| **결정** | 법률 검토는 **법제처 Open API**를 authoritative source로 쓴다. 한쪽 실패는 **degraded result로 표시**하고, 출처·조회 시각·법령 식별자·조문·source channel과 `EXPERT_REVIEW_REQUIRED`를 반환 |
| **왜** | 근거 추적이 가능한 법률 검토. 부분 장애·출처·**비자문 한계**를 결과에 보존 |
| **기각한 대안** | **모델 단독 생성** · 일반 웹 검색 · API only · MCP only |
| **현재 유효** | ✅ 실연동 코드가 `ai/app/legal/moleg.py`에 있다 |

### DEC-008 — A/B는 실제 사용자 실험이 아니다

| | |
|---|---|
| **결정** | 마케팅 A/B는 **시안 상대 비교**다. 실제 사용자 실험이나 전환율이 아니다 |
| **왜** | **오인 방지** |
| **기각한 대안** | market response / purchase probability 예측 |
| **현재 유효** | ✅ 용어와 UI에 반영 |

> 이 결정이 발표에서 특히 좋다 — **"할 수 있는데 일부러 안 한 것"**의 사례다.
> 구매확률을 숫자로 뱉는 편이 더 그럴듯해 보이지만, 그건 거짓말이 된다.

### DEC-004 — 기존 데이터를 이관하지 않기로 했다

| | |
|---|---|
| **결정** | 기존 데이터는 테스트 데이터이므로 **이관하지 않는다** |
| **왜** | 보존 요구가 없고 legacy schema 제거가 단순해진다 |
| **기각한 대안** | transform · archive |
| **현재 유효** | ✅ `ddl-auto=validate`, DB 볼륨 초기화 전제 |

---

## 2. ⚠ 죽은 결정 — 발표에 쓰면 안 된다

`DECISION_LOG.md`에 `ACCEPTED`로 남아 있지만 **제품이 그 길로 가지 않았다.**

| ID | 결정 | 왜 죽었나 |
|---|---|---|
| **DEC-007** | Persona는 토론하지 않고 각각 **독립 interview**를 수행한다 | 「페르소나 → 인터뷰 → 종합」 체인 자체가 **없어졌다**. 패널 트윈 조사로 대체 |
| **DEC-015** | Persona Card는 `Role and Context` / `Problem and Needs` / `Behavior and Decision` **3층** | 같은 이유. 현재는 트윈 카드 뱅크 기반 |
| **DEC-016** | 초기 Final Report는 HTML view + PDF export | 여정 8단계에 최종 보고서 단계가 없다 |
| **DEC-011** | 초기 FILE은 **DOCX와 텍스트**만, PDF 제외 | 시장조사 파이프라인이 **PDF를 읽는다**(`doc_window.py`, PyMuPDF). 범위가 달라졌다 |

> **교훈 자체가 발표 소재가 된다.** "계획 문서와 실제 제품이 갈라지는 것을 어떻게 관리했나" —
> 우리는 `AS_BUILT` 문서를 따로 두고 **"목표"와 "지금 이렇다"를 섞지 않는다**는 규칙을 세웠다.
> (`AS_BUILT_ARCHITECTURE.md` 머리말: *"이렇게 되어야 한다와 지금 이렇다를 섞지 말 것"*)

---

## 3. ⚠ 미결 — 정직하게 말할 것

### OD-008 / DEC-018 — AI 모델·provider·라이브러리 선택 (`DEFERRED`)

| | |
|---|---|
| **상태** | **DEFERRED** — 미결정 방치가 아니라 **due milestone 변경** |
| **결정 내용** | 모델/provider/SDK 선택을 **각 구현 slice 진입 전으로 연기**한다. 그동안 계약은 **provider-neutral**로 유지 |
| **왜** | 공통 task/domain 계약을 특정 공급자 고유 타입에 결합하지 않기 위해. slice별로 품질·비용·latency·보안·운영성을 평가 |
| **기각한 대안** | P2에서 단일 provider 고정 · 모든 provider 동시 지원 · 현재 Spring 어댑터를 목표로 승격 |
| **현실** | 코드에는 `gpt-4o-mini` · `gpt-4o` · `gpt-5.4-nano`가 하드코딩돼 있고 **왜 그것인지 측정한 기록은 없다** |

**발표 전략 (M-09에서 택1)**

- ⓐ 지금 비교·측정해서 결정한다 → 시간과 비용이 든다
- ⓑ **"의도적으로 미결로 두었고, 그래서 계약을 provider-neutral로 설계했다"를 그대로 말한다**

> **ⓑ를 권한다.** 실제로 그것이 설계 의도였고, `AI_PROVIDER=openai|openai-compatible`이라는
> 추상화가 코드에 남아 있어 증거도 있다. PIILOT이 Precision 60.5%를 숨기지 않았듯,
> 미결을 미결이라고 말하는 편이 강하다.
>
> ⚠ 단, "provider를 안 정했다"와 "생각을 안 했다"는 다르다. **연기의 근거가 문서에 있다**는
> 점을 반드시 함께 보여줄 것.

---

## 4. 슬라이드 구성 제안 (별첨 1~2장)

PIILOT처럼 표 하나로 압축한다.

| 결정 | 우리가 고른 것 | 기각한 대안 | 근거 |
|---|---|---|---|
| 데이터 소유권 | Spring 단독 | shared volume, presigned | 무결성을 한 경계에서 |
| 실행 모델 | TaskRun / Attempt 분리 | 단일 트랜잭션 | 중복 채택 방지 |
| 대용량 전송 | bounded inline + chunk | streaming, presigned | 검증 가능성 |
| 법률 근거 | 법제처 API 실연동 | 모델 단독 생성 | 근거 추적 |
| 마케팅 A/B | 시안 상대 비교 | 구매확률 예측 | 오인 방지 |
| **모델 선택** | **의도적 미결 (provider-neutral)** | 단일 provider 고정 | slice별 평가 |

마지막 줄을 빼지 않는 것이 이 표의 핵심이다.
