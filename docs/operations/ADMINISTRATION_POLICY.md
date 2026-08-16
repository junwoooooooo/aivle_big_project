# Administration Policy

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Stable Core administrator and project operations policy
- Supersedes: Deleted legacy admin policy documents
- Implementation Status: PARTIAL

## Authorization

관리 API는 인증된 ADMIN role만 접근한다. 사용자용 Project owner 권한과 관리자 운영 권한을 혼합하지 않으며, 관리자 action은 목적·대상·결과를 audit한다. UI guard만으로 권한을 보장하지 않고 Spring Security와 service 경계에서 검증한다.

## User and role safety

- 마지막 활성 관리자의 role 강등, 비활성화, 잠금 또는 삭제를 금지한다.
- 사용자는 ACTIVE, LOCKED, DISABLED 등 현재 코드가 지원하는 계정 상태를 통해 운영하되 상세 Target enum은 구현 변경 시 별도 contract로 확정한다.
- 계정 제거는 참조 무결성과 감사 가능성을 유지하는 soft delete를 기본으로 한다.
- 관리자는 자신의 권한을 우회해 마지막 관리자 보호를 해제할 수 없다.

## Project operations

관리자는 운영 목적으로 Project 목록·상태·owner·실패 업무를 조회할 수 있다. 사용자 대신 제품 결정을 내리거나 owner scope를 사용자 API에서 우회하는 기능으로 사용하지 않는다. destructive action은 재인증, 명시적 확인, audit를 요구한다.

## Target Admin scope

사용자/역할, 프로젝트 운영, 감사 로그, 범용 Service Policy, TaskRun 상태·실패, Storage 운영 상태, AI Server 연결 상태, 법령 API 연결 상태를 포함한다. fixed Persona, legacy validation, market response, legacy marketing 설정은 포함하지 않는다.
