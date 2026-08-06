import { createAuthApi } from '../../features/auth/api/authApi.js';
import { AuthProvider } from '../../features/auth/AuthProvider.jsx';
import { createAuthSession } from '../../features/auth/authSession.js';
import { createAuthTokenProvider } from '../../features/auth/authTokenStorage.js';
import ServicePolicyProvider from '../../features/service-policy/ServicePolicyProvider.jsx';
import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { createApiClient } from '../../shared/api/apiClient.js';
import GlobalErrorBoundary from './GlobalErrorBoundary.jsx';

function createRuntime() {
  const tokenProvider = createAuthTokenProvider();
  const authTransport = createApiClient({ tokenProvider });
  const authApi = createAuthApi(authTransport);
  const authSession = createAuthSession({ authApi, tokenProvider });
  const apiClient = createApiClient({
    tokenProvider,
    refreshSession: authSession.refreshAccessToken,
  });
  return { authSession, apiClient };
}

const runtime = createRuntime();

export default function AppProviders({
  children,
  authProps,
  apiClient = runtime.apiClient,
  authSession = runtime.authSession,
}) {
  return (
    <GlobalErrorBoundary>
      <ApiClientProvider client={apiClient}>
        <ServicePolicyProvider>
          <AuthProvider session={authSession} {...authProps}>
            {children}
          </AuthProvider>
        </ServicePolicyProvider>
      </ApiClientProvider>
    </GlobalErrorBoundary>
  );
}
