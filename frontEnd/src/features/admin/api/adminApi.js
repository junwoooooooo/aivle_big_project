export function createAdminApi(client) {
  const base = '/admin';
  return {
    overview: (options = {}) => client.get(`${base}/overview`, options).then((r) => r.data),
    users: (params = {}, options = {}) => client.get(`${base}/users?${new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))}`, options).then((r) => r.data),
    user: (id, options = {}) => client.get(`${base}/users/${encodeURIComponent(id)}`, options).then((r) => r.data),
    reauthenticateAdmin: (input) => client.post(`${base}/reauthenticate`, input).then((r) => r.data),
    updateStatus: (id, input, actionToken) => client.patch(`${base}/users/${encodeURIComponent(id)}/status`, input, { headers: actionToken ? { 'X-Admin-Action-Token': actionToken } : {} }).then((r) => r.data),
    updateRole: (id, input, actionToken) => client.patch(`${base}/users/${encodeURIComponent(id)}/role`, input, { headers: actionToken ? { 'X-Admin-Action-Token': actionToken } : {} }).then((r) => r.data),
    deleteUser: (id, input, actionToken) => client.delete(`${base}/users/${encodeURIComponent(id)}`, { body: input, headers: actionToken ? { 'X-Admin-Action-Token': actionToken } : {} }),
    revokeSessions: (id, input) => client.post(`${base}/users/${encodeURIComponent(id)}/sessions/revoke`, input),
    projects: (params = {}, options = {}) => client.get(`${base}/projects?${new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))}`, options).then((r) => r.data),
    project: (id, options = {}) => client.get(`${base}/projects/${encodeURIComponent(id)}`, options).then((r) => r.data),
    audit: (params = {}, options = {}) => client.get(`${base}/audit?${new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))}`, options).then((r) => r.data),
    auditDetail: (id, options = {}) => client.get(`${base}/audit/${encodeURIComponent(id)}`, options).then((r) => r.data),
    settings: (options = {}) => client.get(`${base}/settings`, options).then((r) => r.data),
    updateSetting: (key, input, actionToken) => client.patch(`${base}/settings/${encodeURIComponent(key)}`, input, { headers: actionToken ? { 'X-Admin-Action-Token': actionToken } : {} }).then((r) => r.data),
    services: (options = {}) => client.get(`${base}/ai/services`, options).then((r) => r.data),
    jobs: (options = {}) => client.get(`${base}/jobs`, options).then((r) => r.data),
  };
}
