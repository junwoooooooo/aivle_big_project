# Legal Registry 변경 이력

## legal-registry-v1 — 2026-08-03

- junwoo 법률 파이프라인의 27개 Route와 지배 법령을 현재 AI 내부 실행 구조로 이식했다.
- Route→Category(`category-map-v1`)와 법령·조문 제목→Category(`category-rules-v1`)를 분리했다.
- 법령명은 법제처 현행 법령 정확 일치 검색의 입력으로만 사용한다.
- Registry에 없는 LLM 후보는 검색 Fallback으로 성공 처리하지 않고 `REGISTRY_GAP`으로 반환한다.
