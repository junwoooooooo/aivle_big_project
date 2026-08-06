const CONSERVATIVE_POLICY = Object.freeze({
  registrationEnabled: false,
  documentProcessingEnabled: false,
  maintenanceMode: false,
  clusterPersonaEnabled: false,
});

function normalizePolicy(payload) {
  const data = payload?.data ?? payload;
  return {
    registrationEnabled: data?.registrationEnabled === true,
    documentProcessingEnabled: data?.documentProcessingEnabled === true,
    maintenanceMode: data?.maintenanceMode === true,
    clusterPersonaEnabled: data?.clusterPersonaEnabled === true,
  };
}

export function createServicePolicyApi(client) {
  return {
    async getServicePolicy({ signal } = {}) {
      const payload = await client.get('/service-policy', {
        authenticate: false,
        refreshOnUnauthorized: false,
        signal,
      });
      return normalizePolicy(payload);
    },
  };
}

export function createConservativeServicePolicy() {
  return { ...CONSERVATIVE_POLICY };
}
