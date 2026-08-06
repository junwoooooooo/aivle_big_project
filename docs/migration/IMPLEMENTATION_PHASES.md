# Implementation Phases

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: P0 through P13 delivery guardrails
- Supersedes: Legacy phase readiness and changelog documents
- Implementation Status: PARTIAL

| Phase | Purpose | Prerequisites | Allowed changes | Forbidden changes | Deliverables | Required tests/evidence | Completion condition | Carryover | Next entry condition |
|---|---|---|---|---|---|---|---|---|---|
| P0 | code-based baseline audit | repository access | read-only inspection | file/code/schema change | inventory/classification/risk | branch/HEAD/status/reference searches | audit reported with clean tree | audit document initially absent | P1 decisions available |
| P1 | canonical documentation reset | P0 findings | docs deletion/addition, design move | product code, migration, CI, OpenAPI | canonical doc sets | links, metadata, diff, machine inputs | canonical structure committed | governance/detail correction | P1.1 complete |
| P1.1 | governance and hardening | P1 commit 1549a8e | docs except design/OpenAPI | code, migration, CI, OpenAPI, legacy restore | governance/operations/detail | links/metadata/tables/diff/no-code, PR #14/#15 Remote CI | hardening·closure·CI remediation가 main merge commit 6c43f97에 포함 | 없음 | P2 entry condition 충족 |
| P2 | domain and contract definition | P1.1 COMPLETE, main merge commit 6c43f97 | contract decisions/docs; logical domain schema; state and cardinality definitions; public/internal API JSON schema; contract examples and fixtures | Flyway migration; JPA entity implementation; physical production table creation; Controller/Service runtime implementation | domain/workflow/provenance/public API/internal AI/analysis implementation-ready contracts와 fixtures | consistency/drift/fixture review | P2 due decisions 확정, implementation-ready logical schema/contract/fixture, 문서 간 consistency 검증 | DEC-018 provider/model 선택은 각 provider-dependent slice 진입 전 | P3 ready |
| P3 | Stable Platform/TaskRun | P2 COMPLETE, DEC-017 | tests, /api/v2 base, TaskRun/TaskAttempt, claim/lease, boundary | P4+ product slices | platform migration/code/contracts | Stable Core/Flyway/AI/FastAPI, transaction/concurrency/idempotency | forbidden connections and state ownership verified | performance tuning; DEC-018은 provider-dependent 구현 전 해결 | P4 ready |
| P4 | Idea/Normalization/Legal | P3, DEC-011/DEC-012/DEC-019 | DOCX/plain text Idea, normalization, coordinated Legal slices | Concept+ ahead; PDF/XLSX/PPTX; Storage URL transfer | API/domain/UI/AI/provenance | owner/file/allowlist/payload/legal source/degraded/error/E2E | correction loop/sources verified | optional formats; provider choice는 slice 진입 전 | P5 inputs ready |
| P5 | Concept/Quick | P4, Concept contract, DEC-014 | concept/version/all-candidate Quick | detailed/selection ahead | candidate/quick slices | shared snapshot/provenance/AI/owner/frontend | shortlist inputs verified | scoring refinement | P6 ready |
| P6 | Shortlist/Detailed/Selection | P5, DEC-014 | shortlist-only analysis/selection | Persona+ ahead; Quick 결과 자동 승격 | analysis/selection slices | analysis-specific contracts/user-vs-AI/stale | selected version verified | optional depth | P7 ready |
| P7 | Persona Cards | P6, DEC-015 | Three-Layer PersonaStudy/Card | interviews/discussion; actual-customer/probability claims | card slice | layer/provenance/owner/prohibited-claim validation | cards reviewed/version-linked | extra attributes within accepted layers | P8 ready |
| P8 | Independent Interviews | P7 | interview TaskRuns | discussion/market prediction | interview slices | isolation/retry/stale/owner/E2E | failures isolated | templates | P9 ready |
| P9 | Marketing/Comparison | P8, binary decision | asset/version/comparison | actual-user A/B/probability | workspace slices | Storage/AI binary/claim/UI | relative comparison verified | formats | P10 ready |
| P10 | Persisted Report | P9, DEC-016 | snapshot/version/HTML view/PDF export | runtime-only substitute; initial Markdown export | report slices | version/provenance/storage/PDF/E2E | current/previous/HTML/PDF verified | additional export formats | P11 ready |
| P11 | Admin and Landing Transition | P10 | Target Admin/Service Policy와 Landing content 전환 | legacy schema drop, release 선언 | admin/landing transition | authorization/policy/audit/frontend/accessibility | Target consumers 전환 | cleanup only | P12 removal-ready |
| P12 | Legacy Removal and Database Cutover | P11, replacement tests/consumers | legacy API/route/code/test/artifact 제거와 Baseline 이후 신규 drop migration | 통합 V1 재수정, compatibility 잔존 | clean runtime/schema와 cutover evidence | replacement suite, Flyway fresh/validate, reference scan | legacy 제거와 DB cutover 검증 | release tuning | P13 quality-ready |
| P13 | Integrated Quality, Manual Testing and Release Hardening | P12 | full integration/manual/security/deployment 검증 | 미검증 release 선언 | integrated evidence와 release decision | full local suite, Docker E2E, manual tests, Remote CI/security | release gates와 운영 readiness 확인 | post-release items | release |

각 Phase는 다음 기능을 미리 구현하지 않는다. carryover는 [Phase Status](../governance/PHASE_STATUS.md)에 기록한다.
