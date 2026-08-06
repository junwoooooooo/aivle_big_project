export function safeReturnTo(value, fallback = '/app') {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  ) {
    return fallback;
  }
  try {
    const parsed = new URL(value, 'https://app.local');
    return parsed.origin === 'https://app.local'
      ? `${parsed.pathname}${parsed.search}${parsed.hash}`
      : fallback;
  } catch {
    return fallback;
  }
}
