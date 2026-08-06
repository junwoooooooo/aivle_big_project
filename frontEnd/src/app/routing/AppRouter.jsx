import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';

import AppShell from '../layouts/AppShell.jsx';
import ProjectLayout from '../project-shell/ProjectLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { LoginPage, SignupPage } from '../../features/auth/AuthPages.jsx';
import ProtectedRoute from '../../features/auth/ProtectedRoute.jsx';
import AdminRoute from '../../features/auth/AdminRoute.jsx';
import PublicOnlyRoute from '../../features/auth/PublicOnlyRoute.jsx';
import { ProjectCreatePage, ProjectListPage } from '../../features/projects/ProjectPages.jsx';
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
import { ProjectModulePlaceholder, ProjectOverviewPage } from '../project-shell/ProjectModulePages.jsx';
import { projectRoutes } from './projectRoutes.js';

function ProjectRedirect({ routeKey = 'overview' }) {
  const { projectId } = useParams();
  return <Navigate to={projectRoutes[routeKey](projectId)} replace />;
}

function ProjectSettingsOverlay() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectSettingsSheet /></ProjectProvider>;
}

export default function AppRouter() {
  const location = useLocation();
  const backgroundLocation = location.state?.backgroundLocation;
  return <>
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
            <Route index element={<ProjectRedirect />} />
            <Route path="overview" element={<ProjectOverviewPage />} />
            <Route path="idea" element={<ProjectModulePlaceholder moduleId="idea" />} />
            <Route path="concepts" element={<ProjectModulePlaceholder moduleId="concepts" />} />
            <Route path="concepts/compare" element={<ProjectModulePlaceholder moduleId="conceptCompare" />} />
            <Route path="market" element={<ProjectModulePlaceholder moduleId="market" />} />
            <Route path="business-persona-test" element={<ProjectModulePlaceholder moduleId="businessPersonaTest" />} />
            <Route path="marketing" element={<ProjectModulePlaceholder moduleId="marketing" />} />
            <Route path="settings" element={<ProjectSettingsSheet />} />
            <Route path="settings/general" element={<ProjectRedirect routeKey="settings" />} />
            <Route path="settings/danger" element={<ProjectRedirect routeKey="settings" />} />

            <Route path="get-started" element={<ProjectRedirect routeKey="idea" />} />
            <Route path="legal" element={<ProjectRedirect routeKey="concepts" />} />
            <Route path="journey/concept" element={<ProjectRedirect routeKey="concepts" />} />
            <Route path="journey/concept-analysis" element={<ProjectRedirect routeKey="conceptCompare" />} />
            <Route path="journey/concept-selection" element={<ProjectRedirect routeKey="conceptCompare" />} />
            <Route path="journey/persona" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
            <Route path="journey/interview" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
            <Route path="journey/marketing" element={<ProjectRedirect routeKey="marketing" />} />
            <Route path="journey/final-report" element={<ProjectRedirect />} />
            <Route path="plan/*" element={<ProjectRedirect routeKey="idea" />} />
            <Route path="review/legal" element={<ProjectRedirect routeKey="concepts" />} />
            <Route path="review/*" element={<ProjectRedirect routeKey="market" />} />
            <Route path="validate/marketing/*" element={<ProjectRedirect routeKey="marketing" />} />
            <Route path="validate/*" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
            <Route path="validation/marketing/*" element={<ProjectRedirect routeKey="marketing" />} />
            <Route path="validation/*" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
            <Route path="report/*" element={<ProjectRedirect />} />
            <Route path="*" element={<ProjectRedirect />} />
          </Route>

          <Route path="dashboard" element={<Navigate to="/app" replace />} />
          <Route path="projects" element={<Navigate to="/app/projects" replace />} />
          <Route path="projects/new" element={<Navigate to="/app/projects/new" replace />} />
          <Route path="reports" element={<Navigate to="/app/projects" replace />} />
          <Route path="settings" element={<Navigate to="/app/settings/profile" replace />} />
          <Route path="projects/:projectId" element={<ProjectRedirect />} />
          <Route path="projects/:projectId/overview" element={<ProjectRedirect />} />
          <Route path="projects/:projectId/input" element={<ProjectRedirect routeKey="idea" />} />
          <Route path="projects/:projectId/documents" element={<ProjectRedirect routeKey="idea" />} />
          <Route path="projects/:projectId/structure" element={<ProjectRedirect routeKey="idea" />} />
          <Route path="projects/:projectId/structured-plan/*" element={<ProjectRedirect routeKey="idea" />} />
          <Route path="projects/:projectId/legal-review" element={<ProjectRedirect routeKey="concepts" />} />
          <Route path="projects/:projectId/feasibility" element={<ProjectRedirect routeKey="market" />} />
          <Route path="projects/:projectId/financial" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
          <Route path="projects/:projectId/analyses/:analysis" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
          <Route path="projects/:projectId/personas" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
          <Route path="projects/:projectId/panel-survey" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
          <Route path="projects/:projectId/panel-discussion" element={<ProjectRedirect routeKey="businessPersonaTest" />} />
          <Route path="projects/:projectId/market-validation" element={<ProjectRedirect routeKey="market" />} />
          <Route path="projects/:projectId/report" element={<ProjectRedirect />} />
          <Route path="projects/:projectId/reports/*" element={<ProjectRedirect />} />
          <Route path="projects/:projectId/marketing" element={<ProjectRedirect routeKey="marketing" />} />
          <Route path="projects/:projectId/settings" element={<ProjectRedirect routeKey="settings" />} />
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
  </>;
}
