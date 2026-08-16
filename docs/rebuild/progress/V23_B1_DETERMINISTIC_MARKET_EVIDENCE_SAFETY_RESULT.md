# V23-B1 결정론적 Market Evidence Safety 결과

## 기준

- START SHA: `80622f1d0cdac0b88386f297e92556ab76dffce7`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- authority: `full`; donor는 의도만 선별 참고

## 구현 결과

- PDF는 페이지별 word layout을 검사해 명확한 2단/3단만 열 순서로 읽는다. word 추출 실패, 작은 표본, gutter 부재, 교차 단어 과다, 열 비율 불균형이면 해당 페이지만 기존 `extract_text()`로 돌아간다. OCR·refetch·network는 추가하지 않았다.
- `independent_topdown_blocked`를 `run_id`/`_공용` map으로 바꾸고 과거 샘플 사업 문구를 전역 실행값에서 제거했다.
- 특정 사업에서 온 `침투율`, `단가`, `세그먼트비중`, `추정점유율`, `도달가능비중`, `1년차획득률` default를 `by_role`에서 제거했다. `연환산=12`만 시간 환산 계수로 유지했다.
- harness는 non-observable role 이름뿐 아니라 실제 `assumptions.by_role.<role>.value` 존재까지 확인한다.
- TAM/SAM은 직접 확인된 금액 관측만 투영한다. 동일 원천의 slot 복제는 합치되 독립 출처는 보존하며, 관측이 없으면 null과 미확보 사유를 낸다.
- SOM은 세그먼트 비중 부재를 `1.0`으로 대체하지 않고 가정 곱셈 점추정을 노출하지 않는다.
- 계산 카드 등급이 `근거 없음`이면 값은 null이다. 기존 serializer도 같은 등급의 계산 숫자를 기존 key projection에서 차단한다.
- 성장률 카드는 ratio `0.15`를 내부에 유지하고 `%` 표시값은 `15.0`을 사용한다.

## 계약 확인

- top-level envelope 변경 없음
- FULL stage 이름 변경 없음
- provider/LLM 호출 증감 0
- Backend/Frontend/Migration 변경 0
- `pipeline.py`, ledger/recollect, `product_*`, progress heartbeat 변경 0
- serializer 변경 이유: 카드에서 죽인 `근거 없음` 숫자가 market figure projection에서 다시 살아나는 기존-key 우회 경로를 닫기 위한 1개 조건 추가. 새 field/envelope는 없다.

## 검증

- 명령: `.\ai\.venv\Scripts\python.exe -m pytest ai/tests/test_v23_b1_market_evidence_safety.py ai/tests/test_pipeline_envelope.py`
- 실행 1: 48 passed, 1 failed. 폐기된 business-specific assumption 요인이 반드시 존재한다고 가정한 기존 fixture assertion 실패.
- fixture 계약 수정 후 실행 2: `49 passed in 1.80s`.
- 최종 판정: `READY FOR V23-B2`.
