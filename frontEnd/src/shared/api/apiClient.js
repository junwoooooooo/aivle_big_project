import { ApiError, normalizeApiError } from './apiError.js';
import { buildApiUrl, resolveApiBaseUrl } from './config.js';

function hasJsonContentType(response) {
  return response.headers.get('content-type')?.includes('application/json');
}

async function readResponseBody(response) {
  if (response.status === 204) return null;
  if (hasJsonContentType(response)) return response.json();
  const text = await response.text();
  return text || null;
}

function retryAfterSeconds(response, payload) {
  const retryAfter = response.headers.get('retry-after')?.trim();
  if (retryAfter) {
    const seconds = Number(retryAfter);
    if (Number.isFinite(seconds) && seconds >= 0) return Math.ceil(seconds);

    const retryAt = Date.parse(retryAfter);
    if (Number.isFinite(retryAt)) {
      return Math.max(0, Math.ceil((retryAt - Date.now()) / 1000));
    }
  }

  const bodyValue = payload?.error?.retryAfterSeconds
    ?? payload?.error?.loginAttempt?.retryAfterSeconds
    ?? payload?.retryAfterSeconds;
  const seconds = Number(bodyValue);
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null;
}

function createRequestSignal(externalSignal, timeoutMs) {
  const controller = new AbortController();
  const abort = () => controller.abort(externalSignal?.reason);
  if (externalSignal?.aborted) {
    abort();
  } else {
    externalSignal?.addEventListener('abort', abort, { once: true });
  }
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  return {
    signal: controller.signal,
    cleanup() {
      clearTimeout(timeout);
      externalSignal?.removeEventListener('abort', abort);
    },
  };
}

export function createApiClient({
  baseUrl = resolveApiBaseUrl(),
  fetchImpl = globalThis.fetch,
  tokenProvider,
  onUnauthorized,
  refreshSession,
  timeoutMs = 15000,
} = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('A fetch implementation is required.');
  let refreshPromise = null;

  async function refreshOnce() {
    if (!refreshSession) return false;
    if (!refreshPromise) {
      refreshPromise = Promise.resolve(refreshSession()).finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  async function request(path, options = {}, hasRetried = false) {
    const {
      method = 'GET',
      body,
      headers = {},
      signal: externalSignal,
      requestId,
      authenticate = true,
      refreshOnUnauthorized = true,
      timeoutMs: requestTimeoutMs = timeoutMs,
    } = options;
    const isMultipart = typeof FormData !== 'undefined' && body instanceof FormData;
    const requestHeaders = new Headers(headers);
    let requestBody = body;
    const accessToken = authenticate
      ? await tokenProvider?.getAccessToken?.()
      : null;

    if (accessToken) requestHeaders.set('Authorization', `Bearer ${accessToken}`);
    if (requestId) requestHeaders.set('X-Request-Id', requestId);
    if (body !== undefined && body !== null && !isMultipart) {
      requestHeaders.set('Content-Type', 'application/json');
      requestBody = JSON.stringify(body);
    }

    const requestSignal = createRequestSignal(externalSignal, requestTimeoutMs);
    try {
      if (requestSignal.signal.aborted) {
        throw new DOMException('Request aborted', 'AbortError');
      }
      const response = await fetchImpl(buildApiUrl(baseUrl, path), {
        method,
        body: requestBody,
        headers: requestHeaders,
        signal: requestSignal.signal,
        credentials: 'same-origin',
      });

      if (
        response.status === 401 &&
        refreshOnUnauthorized &&
        !hasRetried
      ) {
        onUnauthorized?.();
        const refreshed = await refreshOnce();
        if (refreshed) {
          requestSignal.cleanup();
          return request(path, options, true);
        }
      }

      const payload = await readResponseBody(response);
      if (!response.ok) {
        throw new ApiError({
          status: response.status,
          code: payload?.error?.code ?? `HTTP_${response.status}`,
          message: payload?.error?.message ?? '요청을 처리하지 못했습니다.',
          fieldErrors: payload?.error?.fieldErrors ?? [],
          retryable: payload?.error?.retryable ?? false,
          requestId: payload?.meta?.requestId ?? response.headers.get('x-request-id'),
          retryAfterSeconds: retryAfterSeconds(response, payload),
          loginAttempt: payload?.error?.loginAttempt ?? null,
        });
      }
      return payload;
    } catch (error) {
      throw normalizeApiError(error);
    } finally {
      requestSignal.cleanup();
    }
  }

  async function stream(path, options = {}, hasRetried = false) {
    const {
      headers = {},
      signal,
      authenticate = true,
      refreshOnUnauthorized = true,
    } = options;
    const requestHeaders = new Headers(headers);
    const accessToken = authenticate
      ? await tokenProvider?.getAccessToken?.()
      : null;
    if (accessToken) requestHeaders.set('Authorization', `Bearer ${accessToken}`);
    if (!requestHeaders.has('Accept')) requestHeaders.set('Accept', 'text/event-stream');

    try {
      const response = await fetchImpl(buildApiUrl(baseUrl, path), {
        method: 'GET',
        headers: requestHeaders,
        signal,
        credentials: 'same-origin',
      });
      if (response.status === 401 && refreshOnUnauthorized && !hasRetried) {
        onUnauthorized?.();
        const refreshed = await refreshOnce();
        if (refreshed) return stream(path, options, true);
      }
      if (!response.ok) {
        const payload = await readResponseBody(response);
        throw new ApiError({
          status: response.status,
          code: payload?.error?.code ?? `HTTP_${response.status}`,
          message: payload?.error?.message ?? '이벤트 연결을 열지 못했습니다.',
          fieldErrors: payload?.error?.fieldErrors ?? [],
          retryable: payload?.error?.retryable ?? response.status >= 500,
          requestId: payload?.meta?.requestId ?? response.headers.get('x-request-id'),
          retryAfterSeconds: retryAfterSeconds(response, payload),
        });
      }
      if (!response.headers.get('content-type')?.includes('text/event-stream')) {
        throw new ApiError({
          status: 502,
          code: 'INVALID_EVENT_STREAM',
          message: '이벤트 응답 형식이 올바르지 않습니다.',
          retryable: true,
        });
      }
      return response;
    } catch (error) {
      throw normalizeApiError(error);
    }
  }

  return {
    request,
    stream,
    get: (path, options) => request(path, options),
    post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
    put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
    patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
    delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
    upload: (path, formData, options) => request(path, { ...options, method: 'POST', body: formData }),
  };
}

export const apiClient = createApiClient();
