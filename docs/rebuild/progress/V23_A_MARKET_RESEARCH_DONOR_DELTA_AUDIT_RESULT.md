# V23-A Market Research Donor Delta Audit 결과

## Authority

- START SHA: `aabd5a9f77a88ffedbac50fa6c0fc49971029fec`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- 비교 authority는 `full`과 `feat/business-validation-refinement`뿐이다.
- Full의 Backend `MarketResearchService/InputFactory/Worker/Run/Version/Contract`, TaskRun lease/deadline, exact FULL→BM version lineage, Market Seed/Selection revision, durable ledger/recollect, `product_*` runtime을 유지한다.
- 결과 envelope는 `runId, conceptId, asOf, generatedAt, mode, stages, degradations, scorecard, market, canvas, bm, evidence, summary, notes` 그대로 유지한다.

## Tree delta summary

- `ai/app/research` 기준 70 files, `+12,540 / -1,696`이다.
- donor의 핵심 추가는 section-oriented reading, re-ask, passage preservation, deterministic promotion, lead ranking, 9-section report 실험이다.
- donor에는 Full-only `market_ledger_artifact.py`, `product_pipeline.py`, `product_market_join.py`, `product_runner.py`, `progress_jsonl.py`가 없다. donor 쪽 삭제 방향은 모두 거부한다.
- `pipeline.py`는 약 44KB→70KB, `serialize.py`는 약 33KB→50KB 수준의 복합 delta다. 두 파일의 전체 복사는 금지하고 함수 단위로 다시 작성한다.

## Identical / no-action

다음은 blob SHA가 동일하므로 재분석·이식 대상이 아니다.

- `research2/adapters/base.py`, `dart.py`, `doc_window.py`, `kosis.py`
- `research2/blocks/a_desk.py`, `b_estimate.py`
- `research2/service/bm_export.py`, `bm_layer.py`, `bm_scorer.py`, `canvas.py`

## Stage exposure 결정

- donor FULL `sections`는 새 public stage로 노출하지 않는다. V23-B2에서 채택할 경우 기존 `collect` 내부 bounded substage로 흡수하고 호출 수·실패는 기존 stage/degradation에 기록한다.
- donor BM `promote`도 public stage로 노출하지 않는다. 검증된 승격은 기존 `cards`/`bm_adapter` 내부 deterministic substage로 흡수한다.
- 따라서 현재 FULL `harness/dryrun/collect/verdict/canvas/cards/summary`, BM `restore/cards/bm_adapter/bm_model` 계약과 진행 UX를 보존한다.

## Transplant matrix

| AREA | FILE/SYMBOL | FULL CURRENT | DONOR DELTA | USER/PRODUCT BENEFIT | RUNTIME RISK | PROVIDER ADAPT? | BACKEND CONTRACT IMPACT | FRONTEND IMPACT | DECISION | V23-B ACTION |
|---|---|---|---|---|---|---|---|---|---|---|
| Runtime authority | `product_pipeline.py`, `product_runner.py`, ledger/progress files | Task workspace, durable artifact restore/commit, subprocess timeout, progress heartbeat | donor tree에 없음 | exact lineage와 paid-run 복구 보존 | 삭제 시 치명적 | NO | NONE | NONE | REJECT donor deletion | Full 그대로 유지 |
| Orchestrator | `pipeline.py` wholesale | Full-only product/recollect 배선 포함 | `sections`, report, judgment/prescription/synthesis, `promote` 결합 | 여러 실험 기능 동시 추가 | 매우 높음 | YES | CONTRACT CHANGE | REQUIRES UI | REJECT | 필요한 함수만 재작성 |
| Serializer | `serialize.py` wholesale | Java exact envelope와 golden fixture 일치 | evidence section/metadata와 새 top-level 4종, BM signature 변경 | 풍부한 내부 정보 | 매우 높음 | NO | CONTRACT CHANGE | REQUIRES UI | REJECT | envelope와 BM handoff 유지, 기존 key로만 투영 |
| PDF fidelity P0 | `pdf_text._page_text/_gutters/_lines` | 일반 `extract_text()` | 다단 조판 감지, page별 fallback, 원문 순서 복원 | quote false-negative 감소 | 가짜 단 감지 | NO | NONE | NONE | TAKE P0 | fixture 기반으로 이식, fallback/사유 보존 |
| Generic contamination P0 | `c_chain.render_report` + `consistency.v1.json` | 특정 카페 회사/상태 문장이 모든 run에 복사될 수 있음 | run-id 또는 `_공용` lookup, 특정 업종 화석 제거 | 다른 사업의 문구 침투 차단 | rule shape 동시 변경 필요 | NO | NONE | NONE | ADAPT P0 | 코드+generic rule+회귀 테스트 동시 반영 |
| Assumption safety P0 | `assumptions.v1.json`, `harness.gate`, `service.verdict/cards`, `series_unit` | 다른 업종 숫자를 가정으로 쓸 수 있고 Product post-filter가 뒤에서 막음 | cross-business 숫자 제거, missing value fail-closed, observation layer, growth % 교정 | 거짓 TAM/SAM/성장 숫자 차단 | 결과 값이 의도적으로 줄 수 있음 | NO | BEHAVIORAL, envelope unchanged | NONE | ADAPT P0 | Full Product post-validation과 중복 제거 후 일관된 fail-closed로 재작성 |
| Section reading P0 | `prompts.EXTRACT_SECTIONS`, `tools/read_sections` intent | slot 질문 중심 추출 | 문서 1건을 7개 의사결정 절 관점으로 읽음 | recall 병목 직접 완화 | 문서당 LLM 1회, 12 workers | YES | NONE if internal | NONE | ADAPT P0 | Full provider/task boundary 안의 bounded extractor로 재작성 |
| Passage fidelity P0 | `tools/read_passages` intent | 수치 중심 quote | 공법·규격·인증·의무·계약 조건도 연속 원문 passage로 보존 | 비수치 사업 조건 확보 | 별도 production wiring 없음 | YES | NONE if existing evidence shape | NONE | ADAPT P0 | section 호출에 통합, exact substring 통과분만 승격 |
| Quote/source gate P0 | `_norm`, `quote_verified`, source URL/retrievedAt | 기존 slot quote gate 존재 | section/passages에도 동일 gate, 실패를 값으로 보존 | recall을 늘려도 precision 유지 | normalization 과허용 가능 | NO | NONE | NONE | TAKE P0 intent | 기존 verification semantics를 재사용하고 false는 evidence 진입 금지 |
| Re-ask P0 | `reask_sections` intent | section별 보충 없음 | 얇은 절을 문서×절로 재질문 | channel/cost/regulation recall 보완 | 호출 폭증, 12 workers | YES | NONE | NONE | ADAPT P0 | missing-section만, 문서별 최대 1회·전역 call cap·deadline remaining gate |
| PDF refetch | `read_sections._refetch_pdfs` | durable 원장 본문 사용 | PDF 재다운로드 후 최신 extractor 적용 | 다단 PDF 복구 | 네트워크 재호출, source 변동 | YES for HTTP boundary | NONE | NONE | DEFER P1 | immutable artifact 정책과 checksum/timeout 설계 후 별도 enable |
| Deterministic promotion P0 | `promote_cards.build` intent | slot cards만 production evidence | verified quote+URL+retrievedAt+source kind 4요건, provenance 보존 | section facts를 evidence/BM에서 실제 사용 | donor `publish.v1`에 종속 | NO | NONE if existing evidence keys only | NONE | ADAPT P0 | generic rules를 새로 작성하고 internal section을 envelope 밖에서 제거 |
| Editorial ranking | `pick_lead.apply` | source/grade 중심 기존 순서 | 후보 ID만 선택, invalid ID 무시, 나머지 보존, 실패 fallback | 본론을 앞으로 이동 | 절당 추가 LLM, hardcoded model | YES | NONE | NONE | DEFER P1 | 우선 deterministic relevance 평가; 유료 LLM ranking은 별도 gate 뒤 |
| BM channel evidence | `bm_adapter.channel_analysis`, `bm/contracts`, `bm/prompt` | channel 전용 evidence label 없음 | promoted CHANNEL을 BM 입력/label로 연결 | 자기입력 자기검증 방지 | 계약 양쪽 parity 필요 | YES for BM model call | ADDITIVE whitelist | NONE | ADAPT P1 | V23-B2에서 AI/Pydantic/Java source-label parity를 한 번에 수정 |
| BM flow regression | `bm/flow.py` | diagnostic context와 financial handoff 유지 | 둘을 제거 | 없음 | downstream 회귀 | NO | CONTRACT BREAK | REQUIRES UI/downstream | REJECT | Full 구현 유지 |
| BM analyze knobs | `bm/analyze.py` | Full diagnostics, max_retries=0 경계 | model별 sampling/effort 처리와 일부 diagnostics 제거 | provider 호환 가능 | model prefix 추측, 관측 손실 | YES | NONE | NONE | ADAPT P1 | shared provider capability로만 구현, diagnostics 보존 |
| Web adapter | `adapters/web.py` | existing query/timeout/extraction | 실제 delta는 extract model 상수 변경뿐 | 검증 전 품질 가능성 | cost/model availability | YES | NONE | NONE | REJECT direct | provider config/eval 없이 모델명 변경 금지 |
| Design/Summary model | `a_design.MODEL`, `summary.SUMMARY_MODEL` | 기존 model 상수 | `gpt-5.6-luna` 직접 지정 | 잠재 품질 개선 | 비용·지원·temperature 차이 | YES | NONE | NONE | REJECT direct | Full provider config와 offline eval 후보로만 기록 |
| Provider options | `runlog.call_options` | 각 호출 방식 혼재 | reasoning prefix에 temperature 제거/output cap 4배 | 일부 400/빈 응답 방지 | prefix 목록 노후·토큰 폭증 | YES | NONE | NONE | ADAPT P1 | model capability 기반 shared provider boundary로 이동 |
| Runner failure detail | `runner._fail` | safe failure 중심 | server-side detail 보존 | 운영 진단 개선 | 민감정보 로깅 | NO | NONE | NONE | ADAPT P1 | redaction 후 structured log에만 기록 |
| Extract caps | `adapters.v1.json` | docs 12, chars 20k, items 8 | 40/60k/20으로 확대 | recall 증가 | token/call 비용 급증 | YES | NONE | NONE | DEFER P1 | 실제 Task budget/deadline 계측 후 결정 |
| Scorecard detail | `tools/scorecard` | 행 수 중심 | counted subjects/layers와 range를 드러냄 | 무엇을 셌는지 투명성 | 현재 7-subject contract와 translation 필요 | NO | ADDITIVE/behavioral | REQUIRES UI if exposed | DEFER P1 | 내부 diagnostics로 먼저 검증, public shape는 별도 계약 |
| Summary principles | `write_report` prompts | existing evidence-cited summary | 숫자 창작 금지, 가정 위장 금지, source/year, gaps 명시 | 요약 관련성·정직성 | report 호출 최대 8회 | YES | NONE if existing summary | NONE | ADAPT P1 | 원칙만 기존 `summary` prompt/gate에 이식, report blob은 금지 |
| 9-section report | `write_report`, `render_*`, `synthesize` | 7-section renderer와 structured envelope | Markdown/section report + GAPS/SYNTHESIS | 풍부한 장문 보고서 | 새 top-level/UI/cost | YES | CONTRACT CHANGE | REQUIRES UI | DEFER P2 | 향후 별도 versioned artifact로 설계 |
| Judgment/prescription | `judge_lines`, `prescribe`, `synthesize` | 현재 envelope에 없음 | 새 판단/처방/합성 blocks | editorial value 가능 | AI 판단과 product contract 혼합 | YES | CONTRACT CHANGE | REQUIRES UI | DEFER P2 | Market evidence closure 후 별도 제품 요구로 평가 |
| Publish rules | `publish.v1.json`, `publish_gate` | 없음 | 433-line gate와 업종별 executable terms 혼재 | promotion 기반 | known red, answer-key contamination | NO | NONE | NONE | REJECT | 파일 복사 금지; business-agnostic 최소 규칙만 재작성 |
| Experimental tools/data | `expected.md`, `runs/**`, probes, reference facts, checklist, corpus/focus/garbage tools | production 대상 아님 | local benchmark/CLI 산출 | 분석 참고 | 오염·용량·재현성 | mixed | NONE | NONE | REJECT production | REFERENCE ONLY, git 복사 금지 |
| CLI rescore guard | `research2/run.py --from` | product source binding이 authority | CLI에서 explicit `--concept` 강제 | 실험 오조합 방지 | product 경로와 무관 | NO | NONE | NONE | DEFER P2 | CLI-only backlog |

## Provider/model and retry/deadline findings

- `read_sections`, `reask_sections`, `read_passages`, `pick_lead`, `write_report`는 직접 `OpenAI()`와 고정 모델을 사용한다. 전부 `PROVIDER ADAPT REQUIRED: YES`다.
- section reader는 12 workers, passage/report는 6 workers다. OpenAI client 기본 2회 retry를 전제로 하며 각 call과 전체 batch의 TaskRun remaining deadline/cancel 경계가 없다.
- Full 공식 FULL budget은 Backend에서 90회다. 현재 collect는 harness 3 + collect 80 = 83회를 예약하므로 7회만 남는다. donor section 시작 최소치는 30 + summary reserve 3 + synthesis 1 = 34회이고, 실측 주석은 132~182 section calls 및 문서×절 re-ask를 기록한다.
- 따라서 donor section path를 그대로 붙이면 fresh Product run에서는 항상 skip되거나, budget을 올리면 현재 cost/deadline 계약을 별도 검증 없이 바꾸게 된다.
- V23-B2 경계는 `max section documents`, `max one re-ask per document`, `max total provider attempts`, `max cumulative wait`, `remaining TaskRun deadline before every call`, timeout/cancel 시 best-effort degradation, no automatic unbounded 429 retry로 고정한다.

## Publish contamination / known donor red

- donor `publish.v1.json` executable values에 `가공식품`, `식품산업`, `식품제조업` 등 특정 업종 용어가 들어 있다.
- donor의 `ai/tests/test_headline_rules.py::test_규칙_파일의_표지에도_업종_낱말이_없다`와 `ai/tests/test_rules_are_business_agnostic.py` parameterized publish case가 이를 잡는 known red다.
- 이 red를 이식 후 수정 대상으로 넘기지 않는다. `publish.v1.json` wholesale은 제외하고, V23-B의 generic promotion rule은 role/section/source/quote 조건만으로 새로 작성한다.

## Backend / Business Validation impact

- P0 deterministic 교정과 internal section extraction은 top-level envelope, Market version creation, durable ledger, exact source version, stale semantics에 변화가 없다. BV impact는 `NONE`이다.
- BM `channel_analysis`를 채택할 때만 Java `SOURCE_LABELS` whitelist와 AI parity의 additive 변경이 필요하다. version/source lineage는 그대로이며 BV impact는 `ADDITIVE`다.
- 새 evidence `section` field를 public envelope에 추가하지 않는다. internal routing 후 기존 evidence key set으로 직렬화한다.
- new `judgment/prescriptions/synthesis/report` top-level fields는 V23-B에서 금지한다.

## Frontend impact

- V23-B1/B2의 채택 범위는 기존 Market/BM renderer가 읽는 `market`, `canvas`, `evidence`, `summary` 안에서만 동작하므로 Frontend 변경은 `NONE`이다.
- donor 9-section report를 그대로 제품화하면 `REQUIRES UI`지만 이 항목은 DEFER다.
- evidence를 section별로 새로 렌더하거나 7과목을 9절로 바꾸는 일도 이번 transplant 범위 밖이다.

## Test inventory for V23-B

Full에서 반드시 보존할 focused inventory:

- AI: `ai/tests/test_pipeline_envelope.py`, `test_market_research.py`, `test_bm_pipeline.py`, `test_bm_contract_parity.py`
- Full-only runtime: `ai/tests/research/test_market_ledger_artifact.py`, `test_market_product_run_directory.py`, `test_market_progress_heartbeat.py`, `test_market_to_bm_product_bridge.py`, `test_v4_market_orchestration.py`
- Research2 representative: `research2/tests/test_step2.py`, `test_step3.py`, `test_step8.py`, `test_step10.py`, `test_step12.py`, `test_harness.py`
- Backend: `MarketResearchContractTests`, `MarketResearchRuntimeContractTests`, `MarketResearchProductInputTests`, `MarketLedgerArtifactServiceTests`

Donor test intent만 re-author할 항목:

- `test_promote_cards.py`: quote/source/retrievedAt gate, magnitude/unit normalization, no invented evidence
- `test_validation_mapping.py`, `test_validation_gate.py`: channel evidence mapping과 invalid evidence filtering
- `test_headline_rules.py`, `test_rules_are_business_agnostic.py`: generic rule guard. donor red fixture/expectation은 복사하지 않는다.
- 신규 focused fixture: multi-column PDF fallback, exact passage substring, duplicate passage de-dup, table/list passage, empty result, bounded re-ask/deadline.

## V23-B implementation boundary

`SPLIT REQUIRED`다. 이유는 파일 수가 아니라 현재 90-call/20-minute Product 계약과 donor의 최소 34회 추가·실측 132~182회 호출 사이의 독립된 provider cost/deadline 경계다.

### V23-B1 — Deterministic Market Evidence Safety Transplant

- multi-column PDF reading + page-local fallback
- cross-business assumption/report-note contamination 제거
- observation-only/fail-closed market calculation과 growth scale 교정
- Full envelope, product runner, durable ledger/recollect, stage names 보존
- provider/model 변경 0, new calls 0, Backend/Frontend contract 변경 0

### V23-B2 — Bounded Section Recall & Evidence Promotion

- section + qualitative passage를 한 bounded extraction contract로 통합
- exact substring/source/retrievedAt gate, de-dup, no-result 정상 처리
- 얇은 절만 최대 1회 re-ask, total attempts/wait/deadline/cancel hard cap
- business-agnostic deterministic promotion, existing evidence keys만 사용
- promotion을 `collect/cards/bm_adapter` 내부 substage로 흡수
- BM channel evidence는 AI/Java parity를 함께 검증
- 기존 summary에 source/year/gap/no-invention 원칙만 적용
- LLM pick-lead, 9-section report, new top-level fields, Frontend는 제외

## Checks and remaining risks

- 코드 변경: 0
- AI/Backend/Frontend/config/migration 변경: 0
- pytest/Gradle/Vitest/ESLint/build/Docker/browser/provider 실행: 0 (분석-only 지시대로 생략)
- 실제 provider cost와 recall improvement는 V23-B2의 bounded offline fixture/approved smoke 전까지 미확정이다.
- 이번 단계의 파일 변경은 AGENTS.md가 강제한 결과/사용자 검증 문서 2개뿐이다.

## 판정

`READY FOR V23-B1`이다. V23-B2는 B1의 deterministic baseline 위에서 budget/deadline 계약을 먼저 고정한 뒤 진행한다.
