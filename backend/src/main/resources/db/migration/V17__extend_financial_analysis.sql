-- V1부터 예약만 돼 있던 financial_analyses 를 실제로 쓴다.
-- 재무는 1 job → 1행이므로 analysis_job_id UNIQUE 제약이 그대로 맞다
-- (법률·타당성이 이 제약 때문에 새 테이블을 판 것과 상황이 다르다).
--
-- 추가하는 것:
--   입력 출처 고정 — 어느 기획서 버전·어느 타당성 결과로 계산했는지가 결과의 일부다.
--   verdict/narrative — 판정은 결정론 계산값, 서술은 AI 산출물이라 따로 둔다.
--   prompt_version — 프롬프트가 바뀌면 재실행 대상인지 판단한다.
-- 가정의 확정 상태(NEEDS_ASSUMPTIONS/CONFIRMED)는 컬럼이 아니라 assumptions_json 안에 실린다.
ALTER TABLE financial_analyses ADD COLUMN structured_plan_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN feasibility_assessment_id BIGINT;
ALTER TABLE financial_analyses ADD COLUMN verdict VARCHAR(40);
ALTER TABLE financial_analyses ADD COLUMN narrative_json TEXT;
ALTER TABLE financial_analyses ADD COLUMN prompt_version VARCHAR(60);

ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_structured_plan
    FOREIGN KEY (structured_plan_id) REFERENCES structured_plans(id);
ALTER TABLE financial_analyses ADD CONSTRAINT fk_financial_feasibility_assessment
    FOREIGN KEY (feasibility_assessment_id) REFERENCES feasibility_assessments(id);

CREATE INDEX idx_financial_project ON financial_analyses(project_id);
