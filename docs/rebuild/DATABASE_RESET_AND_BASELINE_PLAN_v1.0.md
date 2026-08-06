# DATABASE RESET AND BASELINE PLAN v1.0

## 1. 전제

보존할 사용자 데이터가 없으므로 개발 DB를 초기화한다.

## 2. 정책

- 기존 Migration Chain을 새 Pipeline에 계속 누적하지 않는다.
- Auth, User, Project, File, Audit, TaskRun, JobEvent 구조는 재사용 가능 여부를 감사한다.
- 새 Pipeline 테이블을 하나의 Baseline으로 구성한다.
- 과거 SQL은 Git History와 문서 Archive로 보존한다.

## 3. 절차

1. 기존 DB Volume 삭제 공지
2. 현재 Schema Dump를 참고용으로 저장
3. 새 Baseline Migration 작성
4. Clean Migration Test
5. 주요 FK·Unique·Idempotency Test
6. Docker 초기화 확인

## 4. 필수 제약

- Project isolation
- Snapshot hash uniqueness
- Slot index 1~5 uniqueness
- Attempt sequence uniqueness
- Legal Evidence FK
- Selection current uniqueness
- Module handoff idempotency
- Job event sequence uniqueness

## 5. 금지

- Legacy 행 자동 변환
- 의미를 추측한 데이터 이관
- 사용하지 않는 Legacy Table 유지
