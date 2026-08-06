export const CONFIDENCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' };
export const LEVEL_LABELS = {
  PRIMARY: '핵심 검증 대상',
  SECONDARY: '보조 검증 대상',
  LOW_PRIORITY: '후순위',
  INSUFFICIENT_INFORMATION: '정보 부족',
};

export function parseJsonArray(value) {
  if (Array.isArray(value)) return value;
  if (typeof value !== 'string' || value.trim() === '') return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function listItemText(value) {
  if (typeof value === 'string' || typeof value === 'number') {
    return String(value);
  }
  if (!value || typeof value !== 'object') {
    return '';
  }
  return value.question
    ?? value.statement
    ?? value.title
    ?? value.description
    ?? '';
}

export function catalogByCode(catalog) {
  return new Map((catalog ?? []).map((persona) => [persona.personaCode, persona]));
}
