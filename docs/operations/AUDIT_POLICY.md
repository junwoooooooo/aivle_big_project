# Audit Policy

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Stable Core security and administration audit policy
- Supersedes: Deleted legacy audit policy
- Implementation Status: PARTIAL

감사 대상은 인증·계정 보안 변경, 관리자 권한·상태·session·삭제 action, Project 운영 action, Service Policy 변경, TaskRun 운영 action, Storage 정리와 외부 연결 설정 변경이다.

기록은 actor, actor role, action, target type/id/label, result, occurred time, request correlation, 허용되는 범위의 reason과 before/after를 포함하는 방향을 유지한다. 비밀번호, token, secret, 전체 민감 payload, provider raw body는 기록하지 않는다.

성공뿐 아니라 권한 거부, 재인증 실패, action token 오류와 운영 실패도 필요한 범위에서 기록한다. 감사 기록 조회 자체도 관리자 권한과 owner-independent 운영 목적을 명확히 한다. retention/export/immutable storage의 상세 정책은 운영 구현 Phase에서 결정한다.
