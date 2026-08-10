# BusinessFingerprint v1

`business-fingerprint-v1.json`은 Concept 후보 이력·회피 컨텍스트와 의미 기반 차별성 판정에 공통으로 쓰는 서비스 간 계약 fixture다.

- Producer: Backend `ConceptFingerprint.businessSummary`
- Consumers: AI `ConceptCandidateInput`, AI `ConceptDistinctnessJudgeInput`
- 계약 변경: 필드 추가·삭제 또는 타입 변경은 breaking change이며 새 버전 fixture와 양쪽 contract test를 함께 추가한다.
- 보안 경계: 전체 Candidate, raw prompt, Provider 응답 대신 아래 21개 사업 구조 필드만 전달한다.

`concept-candidate-input-v1.json`은 accepted/rejected/current-slot 목록이 모두 비어 있지 않은 실제 Candidate 입력 회귀 fixture다.
