# Session and Reauthentication Policy

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Stable Core session, revocation and privileged action policy
- Supersedes: Deleted session and reauthentication documents
- Implementation Status: PARTIAL

## Session lifecycle

Spring이 access/refresh token 발급, 갱신, 로그아웃과 revocation의 source of truth다. 비밀번호·role·계정 상태·security version 변경처럼 신뢰를 무효화하는 사건은 기존 session을 폐기해야 한다. token과 credential은 로그·audit·문서에 남기지 않는다.

## Revocation

사용자 로그아웃, 계정 잠금/비활성화/삭제, 비밀번호 변경, 관리자 session revoke는 이후 인증을 거부해야 한다. 다중 session 범위와 즉시성은 구현 contract와 테스트로 보호한다.

## Reauthentication and action token

role 변경, 계정 상태 변경, session 일괄 폐기, 삭제 등 고위험 관리자 action은 최근 재인증을 요구한다. 성공한 재인증은 제한된 purpose, actor, expiry와 일회성 소비를 가진 action token으로 표현한다. raw password나 bearer token을 저장하지 않는다.

실패·만료·purpose mismatch·재사용은 거부하고 audit한다. 정확한 TTL과 action 목록은 현재 구현값을 무단 변경하지 않으며 후속 보안 구현 Phase에서 검토한다.
