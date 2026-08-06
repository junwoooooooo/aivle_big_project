const DEFAULT_API_BASE_URL = '/api/v1';

export function resolveApiBaseUrl(
  environment = import.meta.env,
) {
  const configured =
    environment?.VITE_API_BASE_URL?.trim();

  return (configured || DEFAULT_API_BASE_URL)
    .replace(/\/+$/, '');
}

export function buildApiUrl(baseUrl, path) {
  if (!path.startsWith('/')) {
    throw new Error(
      'API path must start with "/".',
    );
  }

  if (path.startsWith('/api/')) {
    if (/^https?:\/\//i.test(baseUrl)) return `${new URL(baseUrl).origin}${path}`;
    return path;
  }
  return `${baseUrl}${path}`;
}
