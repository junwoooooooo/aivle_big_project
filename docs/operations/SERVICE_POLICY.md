# Service Policy

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Generic service availability controls
- Supersedes: Deleted legacy service and Persona policy documents
- Implementation Status: NOT_STARTED

## Target policy directions

| Policy direction | Effect |
|---|---|
| Maintenance mode | 일반 사용자 쓰기 또는 전체 서비스 접근 제한 방향 |
| Project creation allowed | 신규 Project 생성 허용 여부 |
| File upload allowed | IdeaSource FILE과 기타 사용자 파일 업로드 허용 여부 |
| AI execution allowed | 신규 TaskRun 기반 AI 실행 시작 허용 여부 |
| Report generation allowed | FinalReportVersion 및 export 생성 허용 여부 |

정확한 key, default, precedence, emergency override와 API field는 구현 Phase에서 결정한다. 정책은 사용자에게 차단 이유와 재시도 가능성을 일관되게 제공해야 하며 변경은 관리자 권한, 필요 시 재인증, audit를 요구한다.

fixed Persona 활성화, legacy validation, market-response 또는 legacy marketing workflow 설정은 Target policy에 포함하지 않는다.
