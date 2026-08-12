# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 PHASE 0 결과

## 기준선

- `MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- `FULL_START_SHA=c6682c4d802d38ec61f8067819db086afb70938b`
- 브랜치/HEAD: `full` / `c6682c4d802d38ec61f8067819db086afb70938b`
- 시작 worktree: clean
- `git fetch origin --prune`은 Windows 대소문자 비구분 ref의 `origin/AIdev` lock 충돌이 있었으나, main/full ref를 명시 fetch하여 위 SHA를 확정했다.

## 조사 범위

- `ai`, `backend`, `frontEnd`, `docs`, `scripts`
- migration, requirements/package manifests, Docker/Compose/Nginx, env example, ignore 파일
- main 최근 비병합 커밋 80개와 중요 변경 커밋의 실제 changed paths
- 동일 경로 content 차이와 다른 위치의 동등 capability

## Tree inventory

- 전체 tree 차이: 변경 233, main 기준 삭제 698, 추가 169, rename 계열 20
- 상위 영역: AI 749, Backend 185, Frontend 87, Docs 58, 기타 설정·스크립트
- 대량 삭제 차이는 full에서 제거된 legacy와 full-only 신규 플랫폼 파일이 함께 포함되므로 기계적 이식 대상이 아니다.
- full migration 마지막 번호는 V22 TechOps advisory report다.
- main V22 competitor seed는 번호 충돌 때문에 full에서 V23 이상 additive migration으로 변환해야 한다.

## 최초 Capability Ledger

| Capability | main source | full current | 상태 | 조치 |
| --- | --- | --- | --- | --- |
| Market Research2 최신 제품 엔진 | `ai/app/research/**` | 이전 엔진 + full wrappers | REGRESSED | main engine/rules/tests/resources exact transplant, full wrapper 유지 |
| 실제 선택 Concept 제품 경로 | main `pipeline.py`, `runpath.py` | full product adapter 존재, 최신 main 차이 큼 | PARTIAL | main core + full canonical input adapter 결합 |
| quote/value 검증 및 design score | `research2/tools/quote_audit.py`, `design_score.py` | 누락 | MISSING | 이식 및 invariant test |
| dynamic collection/recollect/run recovery | main research2 run/harness/adapters | 일부 이전 구현 | PARTIAL | 원본 이식, full TaskRun wrapper 유지 |
| BM 최신 planning/scoring | main BM/research2 service | full에 일부 존재 | PARTIAL | main core 복구 후 full materializer 연결 검증 |
| TechOps advisor/scaler/evidence | main task 3개 패키지 | 축약 재구현 | REGRESSED | main engine exact transplant, full execution adapter 유지 |
| TechOps canonical runtime | main synchronous 제품 의미 | full TaskRun/Worker/DB/SSE | EQUIVALENT 후보 | main engine 교체 후 semantic 재검증 |
| Twin engine | `ai/app/twin/**` | 대부분 동일, init/runner 차이 | PARTIAL | 함수 단위 감사 후 main invariant 복구 |
| Finance | main estimate | full aidev parity + report hardening | EQUIVALENT 후보 | 역방향 테스트, full stronger 보존 |
| Marketing | main content/image | full R1 hardening | EQUIVALENT 후보 | prompt/contract blob 및 reference call path 재검증 |
| competitor seeds DB | main V22 | full 없음, V22 점유 | MISSING | 다음 migration 번호로 semantic 이식 |
| PDF parser dependency | main `pdfplumber==0.11.9` | version 차이/누락 후보 | MISSING 후보 | dependency diff 후 이식 |
| Vite `/api` proxy | main `vite.config.js` | 차이 있음 | PARTIAL 후보 | 실제 설정 비교 후 이식 |
| user error propagation/cache | main frontend/common | full 일부 이식 | EQUIVALENT 후보 | 테스트로 보장 확인 |

## 주요 gap 분류

- MISSING: Research2 최신 도구·규칙·fixture/quality tests, competitor seed schema, 일부 dependency/runtime resource
- PARTIAL: Market/BM 최신 엔진과 제품 wrapper 연결, Twin runner, frontend Market/BM 최신 품질 UX
- REGRESSED: TechOps AI 3개 패키지가 main 원형이 아닌 축약 재구현
- CONTRACT_MISMATCH: main migration V22와 full V22 번호 충돌
- QUALITY_GAP: main의 Research2 step15~18, design score, quote audit 및 실스택 guard가 full에 일부 없음

## 변경 파일

- 이 문서만 생성했다. PHASE 0에서는 제품 코드를 수정하지 않았다.

## 검증

- branch/HEAD/origin SHA/worktree 확인
- tree 전체 `git diff --name-status` 분류
- full/main migration inventory
- 최근 main non-merge commit inventory
- AI/Backend/Frontend/설정 주요 경로별 diff stat

## 남은 문제 및 continuation

- PHASE 1에서 TechOps 원본 엔진을 먼저 복구하고 full runtime adapter 테스트를 통과시킨다.
- 이어 Market/BM Research2 main tree를 core authority로 이식하되 full-only product wrapper를 보존한다.
- main의 생성 출력/발표자료는 runtime/quality authority와 분리해 실제 호출·테스트에 필요한 자산만 판정한다.

## Git 상태

- 제품 코드 변경 0
- 신규 문서 1
