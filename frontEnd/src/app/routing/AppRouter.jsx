import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';

import AppShell from '../layouts/AppShell.jsx';
import ProjectLayout from '../project-shell/ProjectLayout.jsx';
import PublicLayout from '../layouts/PublicLayout.jsx';
import { LoginPage, SignupPage } from '../../features/auth/AuthPages.jsx';
import ProtectedRoute from '../../features/auth/ProtectedRoute.jsx';
import AdminRoute from '../../features/auth/AdminRoute.jsx';
import PublicOnlyRoute from '../../features/auth/PublicOnlyRoute.jsx';
import { ProjectCreatePage, ProjectListPage } from '../../features/projects/ProjectPages.jsx';
import { IdeaIntakePage } from '../../features/idea-intake/index.js';
import { BusinessProposalWorkspace } from '../../features/concept-portfolio/index.js';
import { MarketIntegrationPage } from '../../features/market-integration/index.js';
import { BusinessModelPage } from '../../features/business-model/index.js';
import { MarketingContentPage } from '../../features/marketing-content/index.js';
import { TechOpsPage } from '../../features/tech-ops/index.js';
import { FinancePage } from '../../features/finance/index.js';
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
import { ProjectOverviewPage } from '../project-shell/ProjectModulePages.jsx';
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
            <Route path="idea" element={<IdeaIntakePage />} />
            <Route path="concepts" element={<BusinessProposalWorkspace />} />
            <Route path="concepts/compare" element={<BusinessProposalWorkspace initialMode="compare" />} />
            <Route path="market" element={<MarketIntegrationPage />} />
            <Route path="business-model" element={<BusinessModelPage />} />
            <Route path="tech-ops" element={<TechOpsPage />} />
            <Route path="finance" element={<FinancePage />} />
            <Route path="marketing" element={<MarketingContentPage />} />
            <Route path="settings" element={<ProjectSettingsSheet />} />
            <Route path="*" element={<ProjectRedirect />} />
          </Route>
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
