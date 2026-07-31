# 법령 조사 파이프라인 (노트북 aivle_BigProject_v2.ipynb 대체)

사업기획서(docx) → 규제 경로 라우팅(LLM) → **법령 레지스트리 조회 + 법제처 Open API 직접 호출(결정론)** → 적합성 선별(LLM) → 리포트.

## 노트북 대비 변경점

1. **상태 드리프트 버그 수정**: 노트북은 셀 전역변수 재실행으로 "라우터가 선택하지 않은
   경로(environmental_waste)가 검색계획에 들어가고, 선택된 경로(consumer_product_safety)는
   검색이 누락"되는 문제가 있었음. 전 단계를 단일 상태 객체(`출력/작업/state.json`)로
   연결하고 `계획 경로 ⊆ 라우터 선택 경로` assert로 차단.
2. **LLM 검색 → 테이블 조회 전환**: 27개 라우트는 닫힌 집합이므로 라우트별 지배 법령을
   `law_registry.json`에 큐레이션(팀 합의 자산). 법령 원문은 법제처 국가법령정보 Open API를
   `urllib`로 직접 호출 — LLM 경유 MCP 검색·텍스트 재추출·수리 단계 전부 제거.
   현행성·MST·시행일자가 API에서 확정되므로 "현행성 확인불가" 문제 소멸.
   LLM 역할은 라우팅(입구)·선별(출구) 2곳만.
3. **결론 자동 생성**: 선별 LLM이 requirement/scope를 근거로 "지금 해야 할 일 Top 5"
   (+ 계획 실행 시 항목)를 action_items로 뽑고, 리포트 맨 위 "결론" 섹션에 근거 조문과
   함께 렌더링. 미래 계획(출원·수출)의 조건부 의무는 Top 5에서 분리.

## 파일

- `legal_pipeline.py` — 파이프라인 본체 (모델: claude-sonnet-5)
- `law_registry.json` — 라우트→법령 큐레이션 테이블 + 고시 포인터 (팀 합의로만 수정)
- `category_map.json` — 27개 라우트 → `LegalCategory` 10개 매핑. **범주 해당 여부(applicability)** 결정 (팀 합의로만 수정)
- `category_rules.json` — 법령·조문 제목 → `LegalCategory`. **근거 배정** 결정 (팀 합의로만 수정)
- `aggregator.py` — 파이프라인 결과를 10개 범주로 접는다
- `REGISTRY_CHANGES.md` — 레지스트리·규칙표 변경 이력
- `service.py` — 백엔드가 호출하는 HTTP 서비스
- `출력/법령조사_리포트.md` — 최종 산출물
- `출력/작업/` — 중간 산출물(state, 프롬프트, LLM 결과, law_cache)

## 실행

```
# 자동 — LLM 백엔드 자동 선택
python legal_pipeline.py run "기획서.docx"

# 백엔드 명시
python legal_pipeline.py run "기획서.docx" --llm anthropic    # ANTHROPIC_API_KEY 사용
python legal_pipeline.py run "기획서.docx" --llm claude-cli   # Claude Code 로그인 세션 사용

# 수동/에이전트 모드 (LLM 호출을 외부에서 수행)
python legal_pipeline.py prepare-route "기획서.docx"     # → 출력/작업/route_prompt.txt
#   route_prompt.txt를 LLM에 입력 → route_result.json 저장
python legal_pipeline.py apply-route 출력/작업/route_result.json   # 법령 수집 → screen_prompt.txt
#   screen_prompt.txt를 LLM에 입력 → screen_result.json 저장
python legal_pipeline.py apply-screen 출력/작업/screen_result.json # → 리포트
```

## LLM 백엔드

`--llm` 또는 `LEGAL_LLM_BACKEND` 환경변수로 고른다. `auto`는 `ANTHROPIC_API_KEY`가 있으면
`anthropic`, 없으면 `claude-cli`를 쓴다.

| 백엔드 | 인증 | 특징 |
|---|---|---|
| `anthropic` | `ANTHROPIC_API_KEY` | SDK 직접 호출. 토큰 효율이 좋다. |
| `claude-cli` | `claude` CLI 로그인 세션 | API 키 불필요. `claude -p --output-format json`을 서브프로세스로 호출. |

`claude-cli`는 호출마다 Claude Code 자체 시스템 프롬프트가 함께 청구된다(측정값 약 47k 토큰,
캐시 포함). 구독 사용량을 소모하므로 **로컬 데모·개발용**이고, 상시 운영에는 API 키가 맞다.
`--allowed-tools ""`로 툴·파일시스템 접근은 차단한다. 타임아웃은 `LEGAL_CLI_TIMEOUT`(기본 900초).

## 백엔드 연동 (HTTP 서비스)

```
pip install -r requirements.txt
uvicorn service:app --host 127.0.0.1 --port 8001 --workers 1
```

`POST /legal-review`가 확정된 StructuredPlan 섹션을 받아 정확히 10개 finding을 돌려준다.
Spring 쪽은 `LEGAL_PROVIDER=pipeline`으로 켠다. `GET /health`로 라우트 수·매핑 수·LLM 백엔드를 확인한다.

`LEGAL_KEEP_WORKDIR=1`로 띄우면 성공한 실행의 작업 디렉터리(state·프롬프트·LLM 결과)를 지우지
않는다. **집계 로직만 고칠 때 LLM을 다시 부르지 않고 저장된 state로 검증**할 수 있다.

### 범주 배정이 두 단계인 이유

| 파일 | 정하는 것 | 근거 |
|---|---|---|
| `category_map.json` | 이 범주가 **해당되는가** | route 판정(해당/적용가능/비해당/불명) |
| `category_rules.json` | 어느 **조문이 근거인가** | 법령 + 조문 제목 |

route 단위로만 배정하면 하나의 route가 N개 범주에 걸릴 때 N개 범주가 완전히 같은 근거를
갖게 된다. 예: `online_sales` → 사업자등록·소비자보호·약관 세 범주에 전자상거래법 조문이
통째로 복사됐다. 조문 단위 배정으로 신고 조문은 사업자등록, 청약철회 조문은 소비자보호로 간다.

법제처 API OC는 현재 `test`(공개 테스트 ID) — 운영 전 https://open.law.go.kr 에서
개인 OC 발급 후 `LAW_API_OC` 교체 권장.

## Sonnet 실험 결과 (2026-07-24, 프레시락 미니 기획서)

Claude Code Sonnet 서브에이전트(claude-sonnet-5)를 라우팅·선별 LLM으로 사용 (비용 0원):

| 항목 | 노트북(gpt-4o-mini + MCP 검색) | 본 파이프라인(Sonnet + 레지스트리) |
|---|---|---|
| 라우팅 | 3경로, environmental_waste 오염 | 6경로(개인정보·수입·지재권 추가 발굴), 폐기물 정확히 비해당 |
| consumer_product_safety 법령 | 검색 자체 누락 (0건) | 전안법 제28~30조(안전기준준수대상생활용품) 확보 |
| online_sales 법령 | MCP 성공했으나 추출 0건 | 전자상거래법 25개 조문(신고·청약철회·금지행위) 확보 |
| 노이즈 | 옥외광고물법·가축분뇨법 혼입 | 없음 |
| 현행성 | 전부 "확인불가" | 전부 API 확정 (MST·시행일자 포함) |
| 선별 | 18건 중 7건 채택, 경로 공백 2개 | 233건 전량 분류 (requirement 45), 경로 공백 0 |

결론: **Sonnet으로 라우팅·선별 모두 가능**. 병목이었던 검색·추출은 LLM에서 제거됐으므로
모델 요구 수준이 낮아짐 — 라우팅은 근거 인용 검증(파이프라인 내장)을 전량 통과.
