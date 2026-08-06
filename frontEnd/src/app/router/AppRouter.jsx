import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';

import AppShell from '../layouts/AppShell.jsx';
import ProjectLayout from '../layouts/ProjectLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { LoginPage, SignupPage } from '../../features/auth/AuthPages.jsx';
import ProtectedRoute from '../../features/auth/ProtectedRoute.jsx';
import AdminRoute from '../../features/auth/AdminRoute.jsx';
import PublicOnlyRoute from '../../features/auth/PublicOnlyRoute.jsx';
import {
  ProjectCreatePage,
  ProjectListPage,
} from '../../features/projects/ProjectPages.jsx';
import WorkspaceHomePage from '../../features/projects/WorkspaceHomePage.jsx';
import { AccountSettingsLayout, AccountSettingsRedirect, ProfileSettingsPage, SecuritySettingsPage } from '../../features/settings/AccountSettingsPages.jsx';
import ProjectSettingsSheet from '../../features/projects/ProjectSettingsSheet.jsx';
import { ProjectProvider } from '../../features/projects/ProjectContext.jsx';
import { AuthPlaceholderPage, NotFoundPage } from '../../pages/FoundationPages.jsx';
import LandingPage from '../../features/landing/LandingPage.jsx';
import AdminShell from '../layouts/AdminShell.jsx';
import AdminOverviewPage from '../../features/admin/pages/AdminOverviewPage.jsx';
import AdminOperationsPage from '../../features/admin/pages/AdminOperationsPage.jsx';
import AdminJobsPage from '../../features/admin/pages/AdminJobsPage.jsx';
import AdminSettingsPage from '../../features/admin/pages/AdminSettingsPage.jsx';
import AdminUsersPage, { AdminUserDetailOverlay } from '../../features/admin/pages/AdminUsersPage.jsx';
import AdminProjectsPage, { AdminProjectDetailOverlay } from '../../features/admin/pages/AdminProjectsPage.jsx';
import AdminAuditPage, { AdminAuditDetailOverlay } from '../../features/admin/pages/AdminAuditPage.jsx';
import { IdeaJourneyPage, LegalJourneyPage } from '../../features/journey/JourneyPages.jsx';
import { ConceptAnalysisPage, ConceptGenerationPage, ConceptSelectionPage } from '../../features/journey/ConceptJourneyPages.jsx';
import { InterviewJourneyPage, PersonaJourneyPage } from '../../features/journey/PersonaInterviewPages.jsx';
import { FinalReportJourneyPage, MarketingJourneyPage } from '../../features/journey/MarketingReportPages.jsx';

function LegacyProjectRedirect({ suffix = '' }) {
  const { projectId } = useParams();
  return <Navigate to={`/app/projects/${projectId}${suffix}`} replace />;
}

function ProjectSettingsOverlay() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectSettingsSheet /></ProjectProvider>;
}

export default function AppRouter() {
  const location = useLocation();
  const backgroundLocation = location.state?.backgroundLocation;
  return (
    <>
    <Routes location={backgroundLocation || location}>
      <Route element={<PublicLayout />}>
        <Route index element={<LandingPage />} />
        <Route element={<PublicOnlyRoute />}>
          <Route path="auth/login" element={<LoginPage />} />
          <Route path="auth/signup" element={<SignupPage />} />
          <Route path="auth/password-reset" element={<AuthPlaceholderPage mode="reset" />} />
        </Route>
        <Route path="login" element={<Navigate to="/auth/login" replace />} />
        <Route path="signup" element={<Navigate to="/auth/signup" replace />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="app" element={<WorkspaceHomePage />} />
          <Route path="app/projects" element={<ProjectListPage />} />
          <Route path="app/projects/new" element={<ProjectCreatePage />} />
          <Route path="app/settings" element={<AccountSettingsLayout />}>
            <Route index element={<AccountSettingsRedirect />} />
            <Route path="profile" element={<ProfileSettingsPage />} />
            <Route path="security" element={<SecuritySettingsPage />} />
          </Route>
          <Route path="app/projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<IdeaJourneyPage />} />
            <Route path="idea" element={<IdeaJourneyPage />} />
            <Route path="legal" element={<LegalJourneyPage />} />
            <Route path="journey/concept" element={<ConceptGenerationPage />} />
            <Route path="journey/concept-analysis" element={<ConceptAnalysisPage />} />
            <Route path="journey/concept-selection" element={<ConceptSelectionPage />} />
            <Route path="journey/persona" element={<PersonaJourneyPage />} />
            <Route path="journey/interview" element={<InterviewJourneyPage />} />
            <Route path="journey/marketing" element={<MarketingJourneyPage />} />
            <Route path="journey/final-report" element={<FinalReportJourneyPage />} />
            <Route path="get-started" element={<Navigate to=".." replace />} />
            <Route path="plan" element={<LegacyProjectRedirect />} />
            <Route path="plan/brief" element={<Navigate to="../settings" replace />} />
            <Route path="plan/documents" element={<LegacyProjectRedirect />} />
            <Route path="plan/structure" element={<LegacyProjectRedirect />} />
            <Route path="review" element={<Navigate to="legal" replace />} />
            <Route path="review/legal" element={<LegacyProjectRedirect suffix="/legal" />} />
            <Route path="review/market" element={<LegacyProjectRedirect suffix="/journey/concept" />} />
            <Route path="review/financial" element={<LegacyProjectRedirect suffix="/journey/concept-analysis" />} />
            <Route path="review/financial/new" element={<LegacyProjectRedirect suffix="/journey/concept-analysis" />} />
            <Route path="review/financial/:analysisId" element={<LegacyProjectRedirect suffix="/journey/concept-analysis" />} />
            <Route path="validate" element={<LegacyProjectRedirect suffix="/journey/persona" />} />
            <Route path="validate/personas" element={<LegacyProjectRedirect suffix="/journey/persona" />} />
            <Route path="validate/interview" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validate/interview/:interviewId" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validate/market-response" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validate/market-response/:predictionId" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validate/marketing" element={<LegacyProjectRedirect suffix="/journey/marketing" />} />
            <Route path="validate/marketing/new" element={<LegacyProjectRedirect suffix="/journey/marketing" />} />
            <Route path="validate/marketing/:contentId" element={<LegacyProjectRedirect suffix="/journey/marketing" />} />
            <Route path="validation" element={<LegacyProjectRedirect suffix="/journey/persona" />} />
            <Route path="validation/interview" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validation/market-response" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
            <Route path="validation/marketing" element={<LegacyProjectRedirect suffix="/journey/marketing" />} />
            <Route path="report" element={<LegacyProjectRedirect suffix="/journey/final-report" />} />
            <Route path="settings" element={<ProjectSettingsSheet />} />
            <Route path="settings/general" element={<Navigate to="../settings" replace />} />
            <Route path="settings/danger" element={<Navigate to="../settings" replace />} />
          </Route>

          <Route path="dashboard" element={<Navigate to="/app" replace />} />
          <Route path="projects" element={<Navigate to="/app/projects" replace />} />
          <Route path="projects/new" element={<Navigate to="/app/projects/new" replace />} />
          <Route path="reports" element={<Navigate to="/app/projects" replace />} />
          <Route path="settings" element={<Navigate to="/app/settings/profile" replace />} />
          <Route path="projects/:projectId" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/overview" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/input" element={<LegacyProjectRedirect suffix="/settings" />} />
          <Route path="projects/:projectId/documents" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/structure" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/structured-plan" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/structured-plan/missing-fields" element={<LegacyProjectRedirect />} />
          <Route path="projects/:projectId/legal-review" element={<LegacyProjectRedirect suffix="/legal" />} />
          <Route path="projects/:projectId/feasibility" element={<LegacyProjectRedirect suffix="/journey/concept" />} />
          <Route path="projects/:projectId/financial" element={<LegacyProjectRedirect suffix="/journey/concept-analysis" />} />
          <Route path="projects/:projectId/analyses/:analysis" element={<LegacyProjectRedirect suffix="/journey/concept-analysis" />} />
          <Route path="projects/:projectId/personas" element={<LegacyProjectRedirect suffix="/journey/persona" />} />
          <Route path="projects/:projectId/panel-survey" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
          <Route path="projects/:projectId/panel-discussion" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
          <Route path="projects/:projectId/market-validation" element={<LegacyProjectRedirect suffix="/journey/interview" />} />
          <Route path="projects/:projectId/report" element={<LegacyProjectRedirect suffix="/journey/final-report" />} />
          <Route path="projects/:projectId/reports/*" element={<LegacyProjectRedirect suffix="/journey/final-report" />} />
          <Route path="projects/:projectId/marketing" element={<LegacyProjectRedirect suffix="/journey/marketing" />} />
          <Route path="projects/:projectId/settings" element={<LegacyProjectRedirect suffix="/settings" />} />
        </Route>

        <Route element={<AdminRoute />}>
          <Route element={<AdminShell />}>
            <Route path="admin" element={<AdminOverviewPage />} />
            <Route path="admin/users" element={<AdminUsersPage />} />
            <Route path="admin/users/:userId" element={<AdminUsersPage />} />
            <Route path="admin/projects" element={<AdminProjectsPage />} />
            <Route path="admin/projects/:projectId" element={<AdminProjectsPage />} />
            <Route path="admin/operations" element={<AdminOperationsPage />} />
            <Route path="admin/jobs" element={<AdminJobsPage />} />
            <Route path="admin/audit" element={<AdminAuditPage />} />
            <Route path="admin/audit/:auditId" element={<AdminAuditPage />} />
            <Route path="admin/settings" element={<AdminSettingsPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
    {backgroundLocation && <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="app/projects/new" element={<ProjectCreatePage />} />
        <Route path="app/projects/:projectId/settings" element={<ProjectSettingsOverlay />} />
        <Route element={<AdminRoute />}>
          <Route path="admin/users/:userId" element={<AdminUserDetailOverlay />} />
          <Route path="admin/projects/:projectId" element={<AdminProjectDetailOverlay />} />
          <Route path="admin/audit/:auditId" element={<AdminAuditDetailOverlay />} />
        </Route>
      </Route>
    </Routes>}
    </>
  );
}
