# Document Removal Manifest

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Phase 1 document disposition
- Supersedes: Legacy document index and traceability matrix
- Implementation Status: IMPLEMENTED

## DELETE_NOW

docs/current 전체, docs/admin 전체, 기존 docs/product 전체, docs/backend, docs/erd, docs/decisions, docs/security, reference의 domain/project/ERD/요구사항 자료를 삭제했다. 별도 archive는 만들지 않으며 Git history를 사용한다.

## RETAIN_DESIGN_REFERENCE

UIUX 통합, UX 스타일, Design Foundation, 반응형 interaction, visual style board, component guide, 화면 정보구조 원본과 reference image 두 개를 docs/reference/design으로 이동했다. 이 자료는 REFERENCE_ONLY다.

## TEMPORARILY_RETAIN_MACHINE_CONTRACT

docs/api/openapi.yaml은 CI와 backend test가 읽는 legacy implementation contract다. docs/guide와 docs/example의 DOCX는 frontend build script와 Dockerfile 입력이다. 세 항목은 canonical이 아니며 해당 code consumer의 대체물이 준비되는 구현 Phase에서 제거 또는 교체한다.
