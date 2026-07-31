-- 범주별 판정에 이른 논리 사슬을 저장한다:
--   기획서 근거 문장 → 걸린 규제 영역 → 발생 의무(조문별) → 위반 시 결과 → 조치·시점
-- 구조화된 근거 조문(설명 포함)은 기존 evidence_json 컬럼을 그대로 쓴다(문자열 배열 → 객체 배열).
-- 이전 리뷰 행은 NULL이며, 조회 계층이 결측을 허용한다.
ALTER TABLE legal_findings ADD COLUMN reasoning_json TEXT;
