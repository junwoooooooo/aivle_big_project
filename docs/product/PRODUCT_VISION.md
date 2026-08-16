# Product Vision

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Product purpose, users, outcomes and delivery direction
- Supersedes: Legacy product briefs and workflow descriptions
- Implementation Status: NOT_STARTED

## Vision

Venture Verify는 완성된 사업계획서를 요구하는 문서 분석기가 아니라, 불완전한 아이디어를 근거 있는 선택과 실행 가능한 검증 결과로 발전시키는 Project workspace다. Project 전체가 하나의 아이디어 검증 과정이며 문서는 여러 입력 수단 중 하나다.

## Primary users

| User type | Need | Product outcome |
|---|---|---|
| Idea owner | 초기 아이디어를 구조화하고 위험과 대안을 확인 | 근거와 version이 연결된 선택 |
| Project collaborator 방향 | 분석 결과를 검토하고 사용자 결정을 남김 | AI 권고와 사람 결정을 분리한 이력 |
| Administrator | 사용자·Project·TaskRun·Storage·외부 연결 운영 | 권한과 감사가 적용된 운영 상태 |

협업 권한의 상세 역할과 sharing model은 현재 확정하지 않는다.

## Product outcome

사용자는 TEXT 또는 FILE logical type의 IdeaSource를 입력하고 정규화된 아이디어를 확인한다. 질문 응답 UI에서 수집한 입력도 TEXT source로 기록한다. 한국 법률 검토 후 여러 concept를 생성·평가하고 shortlist와 상세 분석을 거쳐 하나를 선택한다. 선택 concept에 대해 Three-Layer Persona Card와 Persona별 독립 interview를 수행하고 Marketing Workspace에서 시안을 상대 비교한다. 마지막 결과는 저장 가능한 FinalReportVersion으로 조회·export한다.

## Product principles

- 입력 형식보다 검증과 의사결정 provenance를 우선한다.
- AI 권고와 사용자 최종 결정을 명시적으로 분리한다.
- 결과는 생성 당시 입력 version을 가리키며 upstream 변경 시 stale 여부를 판단한다.
- Persona는 서로 토론하지 않고 독립 관점을 유지한다.
- Marketing A/B는 실제 사용자 실험이나 전환율이 아니라 시안 상대 비교다.
- 법률 결과는 한국 법령 출처와 실패 상태를 숨기지 않는다.
- Target 문서는 목표이며 현재 구현 완료를 뜻하지 않는다.

## Delivery direction

P2에서 상세 domain/contract를 확정하고 P3에서 Stable Platform과 TaskRun 기반을 마련한다. P4~P10에서 workflow를 vertical slice로 구현한다. P11은 Admin/Landing 전환, P12는 legacy 제거와 database cutover, P13은 통합 품질·수동 테스트·release hardening을 수행한다.
