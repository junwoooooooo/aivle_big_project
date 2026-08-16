# Final Entity–Table Inventory

R7A compile-green inventory. Source of truth is the current `@Entity`/`@Table` mapping under `backend/src/main/java`.

## Foundation and operations

| Entity | Table | Package | Keep reason | Main FK targets | Legacy | Baseline |
|---|---|---|---|---|---|---|
| User | `users` | `user.entity` | Authentication and ownership | — | No | Yes |
| RefreshToken | `refresh_tokens` | `auth` | Refresh-token lifecycle | `users` | No | Yes |
| Project | `projects` | `project.entity` | Ownership boundary; no Journey stage | `users` | No | Yes |
| StoredFile | `stored_files` | `file.entity` | Shared immutable file metadata | — | No | Yes |
| AuditEvent | `audit_events` | `audit` | User/admin audit trail | `users`, logical `projects` | No | Yes |
| ServiceSetting | `service_settings` | `admin` | Registration and maintenance policy | logical `users` | No | Yes |
| AdminActionToken | `admin_action_tokens` | `admin` | Time-bounded admin reauthentication | logical `users` | No | Yes |
| TaskRun | `task_runs` | `taskrun.domain` | Durable new-pipeline execution | `projects` | No | Yes |
| TaskAttempt | `task_attempts` | `taskrun.domain` | Lease/attempt history | `task_runs` | No | Yes |
| TaskResult | `task_results` | `taskrun.domain` | Validated immutable task result | `task_runs`, `task_attempts` | No | Yes |
| JobEvent | `job_events` | `jobevent` | SSE/replay event log | `projects`, `task_runs` | No | Yes |

## Idea Brief

| Entity | Table | Package | Keep reason | Main FK targets | Legacy | Baseline |
|---|---|---|---|---|---|---|
| IdeaBrief | `idea_briefs` | `pipeline.idea.domain` | Canonical versioned Idea Brief | `projects`, `users`, `task_runs`, self | No | Yes |
| IdeaBriefField | `idea_brief_fields` | `pipeline.idea.domain` | Field decision/provenance | `idea_briefs` | No | Yes |
| IdeaQuestion | `idea_questions` | `pipeline.idea.domain` | Bounded Question Cards | `idea_briefs` | No | Yes |
| IdeaAnswer | `idea_answers` | `pipeline.idea.domain` | Idempotent answers | `idea_briefs`, `idea_questions` | No | Yes |

`idea_brief_attachments` is a required join table without a standalone entity; it links `idea_briefs` to `stored_files`.

## Legal context and Concept Factory

| Entity | Table | Package | Keep reason | Main FK targets | Legacy | Baseline |
|---|---|---|---|---|---|---|
| LegalContextPack | `legal_context_packs` | `pipeline.legal.domain` | Shared legal context | `projects`, `idea_briefs` | No | Yes |
| LegalEvidence | `legal_evidence` | `pipeline.legal.domain` | Official evidence metadata | `legal_context_packs` | No | Yes |
| ConceptFactoryRun | `concept_factory_runs` | `pipeline.concept.domain` | Five-slot run | `projects`, `idea_briefs`, `users`, `task_runs` | No | Yes |
| ConceptSlot | `concept_slots` | `pipeline.concept.domain` | Fixed slot 1–5 state | `concept_factory_runs` | No | Yes |
| ConceptAttempt | `concept_attempts` | `pipeline.concept.domain` | Slot attempt history | `concept_slots`, `task_runs` | No | Yes |
| Concept | `concepts` | `pipeline.concept.domain` | Candidate/public result | `concept_factory_runs`, `concept_slots`, `idea_briefs` | No | Yes |
| ConceptLegalAssessment | `concept_legal_assessments` | `pipeline.legal.domain` | Legal eligibility decision | `concepts`, `legal_context_packs` | No | Yes |
| ConceptLegalEvidenceLink | `concept_legal_evidence_links` | `pipeline.legal.domain` | Assessment evidence trace | `concept_legal_assessments`, `legal_evidence` | No | Yes |
| ConceptRejectionSummary | `concept_rejection_summaries` | `pipeline.concept.domain` | Safe rejected-attempt summary | `concept_slots` | No | Yes |

## Selection, handoff, planning, and marketing

| Entity | Table | Package | Keep reason | Main FK targets | Legacy | Baseline |
|---|---|---|---|---|---|---|
| ConceptSelection | `concept_selections` | `pipeline.selection.domain` | Explicit current selection | `projects`, `concepts`, `users` | No | Yes |
| SelectedConceptSnapshot | `selected_concept_snapshots` | `pipeline.selection.domain` | Immutable selected snapshot | `concept_selections`, `projects`, `concepts`, `users`, self | No | Yes |
| ModuleHandoff | `module_handoffs` | `pipeline.integration.domain` | Immutable external input contract | `projects`, selected/finalized snapshots, `users` | No | Yes |
| ModuleRun | `module_runs` | `pipeline.integration.domain` | External run state | `module_handoffs`, `projects` | No | Yes |
| MarketAnalysisResult | `module_results` | `pipeline.integration.domain` | Accepted market result | `module_runs`, `selected_concept_snapshots` | No | Yes |
| PlanningChangeProposal | `planning_change_proposals` | `pipeline.integration.domain` | Evidence-backed change proposal | `module_results`, `projects` | No | Yes |
| PlanningChangeDecision | `planning_change_decisions` | `pipeline.planning.domain` | Explicit proposal decision | `planning_change_proposals`, `projects`, `users` | No | Yes |
| PlanningSnapshot | `planning_snapshots` | `pipeline.planning.domain` | Deterministic planning revision | `projects`, `selected_concept_snapshots`, `users`, self | No | Yes |
| FinalizedPlanningSnapshot | `finalized_planning_snapshots` | `pipeline.planning.domain` | Immutable finalized planning | `projects`, `planning_snapshots`, `selected_concept_snapshots`, `users`, self | No | Yes |
| MarketingContent | `pipeline_marketing_contents` | `pipeline.marketing.domain` | New finalized-planning content | `projects`, `finalized_planning_snapshots`, `task_runs`, `users` | No | Yes |
| MarketingContentRevision | `pipeline_marketing_content_revisions` | `pipeline.marketing.domain` | Immutable edit/revision history | `pipeline_marketing_contents`, `users` | No | Yes |
| MarketingAsset | `pipeline_marketing_assets` | `pipeline.marketing.domain` | Revision artifact reference | content and revision tables | No | Yes |

## Exclusions

No retained entity maps to Journey, document/structured-plan, legacy legal/feasibility/financial, Persona/validation/simulation/report, legacy marketing workspace, or legacy AI artifact/job tables. `pipeline_module_runs` and the earlier generic `planning_snapshots`/`module_handoffs` scaffold from V6 are also excluded because no current entity owns those obsolete shapes.
