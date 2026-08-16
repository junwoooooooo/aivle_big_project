# V23-B2 제한적 섹션 회수·근거 승격 결과

## 결과

- 시작 SHA: `0dfbbc8f9477d63ccc237539fbfb93f2346cdb95`
- donor SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- B1 보완: 동일 관측이 TAM/SAM 공용 슬롯에 걸리면 두 값 모두 만들지 않고, 단일 `claim_type` 관측만 해당 층위의 authority로 사용한다.
- 섹션 회수: 기존 FULL fresh/recollect 수집 본문만 대상으로 최대 문서 8개, base 8회, re-ask 2회, 총 10회, worker 4개, 호출 30초, substage 120초, Task deadline guard 30초로 제한했다.
- 예산: FULL 90→96, BM 1 유지, summary reserve 3을 선점한다.
- passage gate: URL·조회일·source kind를 요구하고 NFKC/공백/문장부호 정규화 후 원문 substring과 일치하는 10~500자 quote만 받는다. 숫자는 quote 안에서 확인되고 기존 parser가 읽을 때만 값으로 승격한다.
- 승격: 7개 내부 section별 최대 4건(총 28건), source identity·section·정규화 quote 기반 stable ID를 사용한다. cap 초과는 `SECTION_EVIDENCE_SAMPLED`로 기록한다.
- FULL 연결: 기존 `evidence[]` shape로 병합하고 동일 merged card를 summary 입력에 전달한다. 새 public field/stage는 없다.
- BM 연결: Backend가 전달한 exact FULL `marketResultJson`의 `C-SEC-` + `채널·유통 조건` + generic anchor 근거만 `channel_analysis` 비교 재료로 사용한다. AI/Java source label을 함께 맞췄다.
- 실패 의미: section timeout/provider/JSON/quote 실패는 collect degradation이며 기존 A4 결과를 폐기하지 않는다.

## 검증

- AI focused 최종 결과: 87 passed. 실행 3회(출력 회수 실패 1회, fixture signature 회귀 확인 1회, 수정 후 PASS 1회).
- Backend focused 최종 결과: 12 passed. 명령 시작 4회(샌드박스 다운로드 차단 2회, fixture 컴파일 실패 1회, 수정 후 PASS 1회).
- Frontend: 변경 및 테스트 0.
- 실제 provider 호출: 0.
- Migration: 없음.

## 보류

- 실제 passage 품질, quote reject 비율, 호출 수·벽시계, payload 크기와 BM 채널 활용 품질은 V23-C의 제한적 provider smoke에서 확인한다.
- `PROVIDER QUALITY REVIEW PENDING`.

