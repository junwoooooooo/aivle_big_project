# R1 Active Surface Removal Audit

## Purpose

This R0 audit records the legacy Route, Navigation, and Controller surfaces that R1 must remove from user exposure immediately. It does not authorize deleting their implementation before the replacement stage is ready; dead implementation deletion remains an R7 concern unless another governing contract assigns an earlier replacement.

Audit baseline:

- Branch: `rebuild/new-pipeline-v1`
- HEAD: `1b6e1d2f341b06aa51608ec848f00eccf72a084a`
- Audited on: 2026-08-06

## Frontend route surfaces

Primary owner: `frontEnd/src/app/router/AppRouter.jsx`

R1 must replace the legacy components currently mounted at these active project paths:

- `/app/projects/:projectId` and `/app/projects/:projectId/idea`
- `/app/projects/:projectId/legal`
- `/app/projects/:projectId/journey/concept`
- `/app/projects/:projectId/journey/concept-analysis`
- `/app/projects/:projectId/journey/concept-selection`
- `/app/projects/:projectId/journey/persona`
- `/app/projects/:projectId/journey/interview`
- `/app/projects/:projectId/journey/marketing`
- `/app/projects/:projectId/journey/final-report`

R1 must also remove or redirect without exposing a legacy screen for these aliases:

- Project aliases under `plan/**`, `review/**`, `validate/**`, `validation/**`, and `report`
- Old project routes under `/projects/:projectId/**`, including documents, structured plan, legal review, feasibility, financial analysis, personas, panel survey/discussion, market validation, report, and marketing
- Old workspace aliases `/dashboard`, `/projects`, `/reports`, and `/settings` may redirect only to a valid new shell destination; they must not restore a legacy product surface

## Frontend navigation surfaces

R1 must update these files before the new shell is exposed:

- `frontEnd/src/app/layouts/ProjectLayout.jsx`: removes `CURRENT_JOURNEY_STEPS`, `LEGACY_MVP_STEPS`, the Journey pager, and the legacy stepper.
- `frontEnd/src/app/layouts/AppShell.jsx`: removes legacy `plan`, `review`, `validate`, and `report` help/navigation context.
- `frontEnd/src/features/projects/routing/projectRoutes.js`: replaces legacy route helper output with the new route map.
- `frontEnd/src/features/projects/model/projectWorkflowModel.js`: replaces global stage/workflow navigation with independent module status.
- `frontEnd/src/features/projects/ProjectAreaPages.jsx`: removes Plan/Review/Validate/Report navigation cards from active exposure.
- `frontEnd/src/app/router/AppRouter.jsx`: stops importing and mounting `frontEnd/src/features/journey/**` pages.

The legacy feature implementations remain classified `DELETE_IN_R7`; R1 removes their reachability.

## Backend controller surfaces

R1 must prevent the following legacy product controllers from being registered as user-facing endpoints, or replace their mappings with approved new-pipeline controllers. R1 must not merely hide their frontend links.

### Journey v2

- `backend/src/main/java/com/aivle/backend/journey/JourneyController.java`
- `backend/src/main/java/com/aivle/backend/journey/ConceptJourneyController.java`
- `backend/src/main/java/com/aivle/backend/journey/LegalPrecheckController.java`
- `backend/src/main/java/com/aivle/backend/journey/PersonaJourneyController.java`
- `backend/src/main/java/com/aivle/backend/journey/MarketingReportJourneyController.java`
- `backend/src/main/java/com/aivle/backend/journey/conversation/IdeaWorkspaceController.java`
- `backend/src/main/java/com/aivle/backend/journey/boundary/RegulatoryBoundaryController.java`
- `backend/src/main/java/com/aivle/backend/journey/conceptcore/ConceptExplorationController.java`

### Legacy v1 product modules

- `backend/src/main/java/com/aivle/backend/document/controller/DocumentController.java`
- `backend/src/main/java/com/aivle/backend/document/controller/StructuredPlanController.java`
- `backend/src/main/java/com/aivle/backend/analysis/legal/controller/LegalReviewController.java`
- `backend/src/main/java/com/aivle/backend/analysis/feasibility/controller/FeasibilityAssessmentController.java`
- `backend/src/main/java/com/aivle/backend/analysis/financial/FinancialAnalysisController.java`
- `backend/src/main/java/com/aivle/backend/analysis/financial/FinancialAnalysisSourceController.java`
- `backend/src/main/java/com/aivle/backend/persona/catalog/controller/BaselinePersonaController.java`
- `backend/src/main/java/com/aivle/backend/persona/catalog/controller/ProjectPersonaCatalogController.java`
- `backend/src/main/java/com/aivle/backend/persona/recommendation/controller/PersonaRecommendationController.java`
- `backend/src/main/java/com/aivle/backend/validation/PersonaValidationController.java`
- `backend/src/main/java/com/aivle/backend/validation/panel/PanelInterviewController.java`
- `backend/src/main/java/com/aivle/backend/validation/market/MarketResponseController.java`
- `backend/src/main/java/com/aivle/backend/marketing/content/MarketingContentController.java`

## Controllers explicitly outside this removal list

Authentication, project ownership, admin, TaskRun, JobEvent/SSE, audit, shared job infrastructure, and global exception handling are retained or adapted under the governing contracts. Their presence is not evidence that a legacy product surface may remain reachable.

## R1 acceptance condition

R1 may complete only when the new project shell and route map are active, no navigation renders a legacy Journey/Plan/Review/Validate/Report entry, direct legacy URLs cannot render a legacy page, and the listed legacy product controllers are not user-facing.
