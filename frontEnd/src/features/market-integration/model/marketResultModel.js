export const DECISION_LABELS = Object.freeze({
  PENDING: '검토 전', ADOPT: '채택', PARTIALLY_ADOPT: '부분 채택', REJECT: '거절',
});

export function stringifyPlanningValue(value) {
  if (value == null) return '입력 없음';
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.join(', ');
  return Object.entries(value).map(([key, item]) => `${key}: ${typeof item === 'object' ? JSON.stringify(item) : item}`).join('\n');
}

export function parsePartialValue(text) {
  const trimmed = text.trim();
  if (!trimmed) return null;
  try { return JSON.parse(trimmed); } catch { return trimmed; }
}
