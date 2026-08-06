# LOCAL FAST EXECUTION PROFILE

## 1. 목적

R0~R6에서는 Codex가 구현을 우선하고, 시간이 오래 걸리는 검증은 사용자가 직접 수행한다.

이 문서는 제품 계약을 변경하지 않는다.
테스트 실행 주체와 실행 시점만 조정한다.

## 2. Codex 시간 제한

- 한 실행 단위는 최대 35분을 목표로 한다.
- 35분 안에 범위를 끝내지 못하면 무리하게 확장하지 않는다.
- 완료된 변경, 미완료 항목, 다음 시작점을 문서화하고 중단한다.
- 다음 R 단계로 자동 진행하지 않는다.

## 3. Codex가 항상 수행할 것

- branch, HEAD, git status 확인
- 현재 단계에 필요한 문서만 읽기
- 관련 파일과 직접 의존 파일만 검사
- 실제 구현
- git diff --check
- 변경된 언어의 가벼운 구문 검사
- 가능하면 직접 관련된 테스트 1개 또는 작은 Targeted 묶음
- progress/Rn_RESULT.md 작성
- verification/Rn_USER_VERIFICATION.md 작성

## 4. Codex가 R0~R6에서 기본적으로 수행하지 않을 것

- Backend 전체 테스트
- AI 전체 테스트
- 전체 postgresTest
- Testcontainers 전체 실행
- Frontend baseline 전체
- Frontend production build
- Docker Compose 전체 재빌드
- 실제 OpenAI Provider Smoke
- 브라우저 수동 테스트
- 모바일·접근성 수동 테스트
- CI 전체 실행
- commit
- push

사용자가 명시적으로 요청한 경우에만 수행한다.

## 5. 가벼운 검증 상한

- 개별 명령 최대 5분
- 같은 실패 명령 재실행 최대 1회
- Gradle 다운로드, Docker 이미지 Pull, Testcontainers 기동으로 오래 걸리면 중단
- 환경 문제를 제품 코드 수정으로 우회하지 않음
- 실행하지 않은 테스트는 실행했다고 주장하지 않음

## 6. 사용자 검증 문서

각 단계의 USER_VERIFICATION 문서에는 다음을 기록한다.

- 사용자가 실행할 정확한 명령
- 예상 소요 범위
- 성공 기준
- 브라우저에서 확인할 화면
- 실패 시 수집할 로그
- 다음 단계 진행 가능 조건

## 7. R7

전체 회귀, Docker E2E, Provider Smoke, Browser, Mobile, A11y는 R7에서
사용자가 직접 수행하거나 명시적으로 Codex에 요청한다.
