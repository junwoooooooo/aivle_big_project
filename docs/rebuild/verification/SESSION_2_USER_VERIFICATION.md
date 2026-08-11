# Session 2 사용자 검증 안내

## 검증 전제

- branch: `integration/full-transplant-v1`
- 코드 기준: Session 2 commit `transplant: market bm and twin modules`
- 외부 key와 Twin Bank는 저장소에 commit하지 않는다.
- 아래 검증은 Codex가 금지된 LIVE 검증 범위를 사용자가 별도로 확인하는 절차다.

## 1. 환경 준비

1. `.env.example`을 참고해 Market Research 전용 OpenAI wallet, KOSIS, DART, Tavily 값을 로컬 `.env`에 설정한다.
2. 재배포 권한이 있는 Twin Bank를 `target/ai/app/twin/bank`에 별도로 배치한다.
3. `manifest.json`과 frame 파일이 donor 계약에 맞는지 확인한다.
4. `docker compose config`에서 AI volume이 아래처럼 read-only인지 확인한다.

```text
./ai/app/twin/bank:/app/app/twin/bank:ro
```

## 2. Market LIVE 소규모 검증

1. 새 프로젝트에서 CPV2 임의 Concept 하나를 명시적으로 선택한다.
2. required hypotheses와 final legal/handoff를 확정하여 Market Seed가 current 상태인지 확인한다.
3. Market 화면의 `조사 기준` 카드에 선택한 Concept 이름, selection revision, Seed ID가 표시되는지 확인한다.
4. Market 실행 후 즉시 202와 TaskRun ID가 반환되는지 확인한다.
5. Work Center에서 실제 stage/status가 보이고, 완료 이벤트 뒤 화면이 새로고침 없이 canonical 결과를 갱신하는지 확인한다.
6. A1/A2/A3에서 KOSIS/DART/Web 수집이 실행되며 sample `beauty-noshow`, `household-ledger`, `pet-treat` 결과로 대체되지 않는지 확인한다.
7. TAM/SAM/성장률/가격, 7개 section, grade, evidence/quote/source/caveat, 계산식/input/assumption, not-found 5분류를 확인한다.
8. provider/adapter 장애를 한 번 유도해 FAILED/PARTIAL/DEPENDENCY_UNAVAILABLE 의미가 fake success로 바뀌지 않는지 확인한다.
9. retry 시 기존 TaskRun이 revive되지 않고 새 TaskRun이 생기며 history가 남는지 확인한다.

## 3. BM 검증

1. 고객 관계/핵심 활동/핵심 자원/핵심 파트너와 예산/기간/인원을 입력하고 저장한다.
2. 빈 항목이 있을 때 confirm dialog와 의미 설명이 보이는지 확인한다.
3. BM 실행 후 TaskRun input 감사 로그에서 MarketResearchVersion ID와 BM plan revision이 고정됐는지 확인한다.
4. 실행 중 Market을 다시 생성해도 이미 시작한 BM의 source version이 바뀌지 않는지 확인한다.
5. decision/confidence/summary, fit/consistency, SWR, BMC 9칸, cell status/kind/reason/source/evidence/caveat, legal, financial handoff를 확인한다.

## 4. Twin 검증

1. Twin 화면에서 현재 selected Concept를 source로 표시하는지 확인한다.
2. Stimulus Draft 실행이 202 TaskRun으로 Work Center에 나타나고, SSE 완료 뒤 draft picker가 갱신되는지 확인한다.
3. axis/rationale/X/Y/drop reason, pair editor와 serviceable gate를 확인한다.
4. 50/100/300 각 표본에서 예상 응답/시간/MDE warning이 바뀌는지 확인한다.
5. 작은 허용 표본으로 Survey를 실행해 winner/not measurable, composition, reason, profiles/interviews, Δ/CI/MDE/position/content share/classes/short-cell/caveat/KISDI footnote를 확인한다.
6. Twin Bank mount를 제거한 별도 실행에서 `TWIN_BANK_UNAVAILABLE`로 안전 실패하며 빈 synthetic result가 생성되지 않는지 확인한다.
7. 실 Twin Bank 대규모 실행은 운영 예산과 개인정보 취급 절차를 확인한 뒤 별도 수행한다.

## 5. current/stale/SSE 검증

1. 각 current GET을 반복 호출해 DB state/version 수가 변하지 않는지 확인한다.
2. 상위 selected Concept 또는 Market Seed를 변경해 기존 Market/Twin이 `STALE`로 표시되는지 확인한다.
3. current Market version 변경 후 과거 BM이 current로 계속 표시되지 않는지 확인한다.
4. Job SSE와 Project SSE 재연결, Last-Event-ID, 브라우저 새로고침 뒤 canonical 복구를 확인한다.

## 6. 이번 사용자 검증 범위

Codex가 실행하지 않은 Provider LIVE, MOLEG LIVE, 실 KOSIS/DART/Tavily full research, 실 Twin Bank 대규모 Survey, 전체 Browser E2E, 전체 사용자 Docker 검증은 이 문서에 따라 사용자가 수행한다. 실패 시 TaskRun ID, JobEvent sequence, safe error code, Market/BM/Twin source lineage ID를 함께 기록한다.
