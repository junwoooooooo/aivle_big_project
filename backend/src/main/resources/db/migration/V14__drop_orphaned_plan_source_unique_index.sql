-- V13이 uk_plan_source_document_version 제약을 삭제했지만, 기존 H2 파일 DB에서는
-- fk_plan_source가 그 제약의 백킹 UNIQUE 인덱스를 소유(재사용) 중이어서 인덱스가
-- 유니크 상태로 남는다 → 파생 버전(v2) 저장 시 23505. FK를 재바인딩하고 고아 인덱스를 제거한다.
-- 신규 H2/PostgreSQL에는 해당 인덱스가 없으므로 no-op이다.
ALTER TABLE structured_plans DROP CONSTRAINT fk_plan_source;
DROP INDEX IF EXISTS "uk_plan_source_document_version_INDEX_E";
ALTER TABLE structured_plans ADD CONSTRAINT fk_plan_source
    FOREIGN KEY (source_document_version_id) REFERENCES document_versions(id);
