# V2-10D 사용자 검증

Backend Finance 표적 테스트, AI `test_finance_estimate.py`와 type alignment, Frontend Finance page/model 테스트와 targeted ESLint를 실행한다.

브라우저에서 AI 추정의 가정·설명·신뢰도를 확인하고 채택 시 source가 AI_ESTIMATE/ACCEPTED인지, 수정 후 채택은 USER_INPUT/USER_EDITED_ACCEPTED인지 확인한다. 다른 추정 요청 후 version과 값이 달라야 한다. PROPOSED만 남은 필수값으로 Snapshot finalize가 거부되고, CAC가 마케팅비·영업비·신규고객 수로 서버 계산되는지 확인한다.
