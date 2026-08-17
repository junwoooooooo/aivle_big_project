const REPORT_LABELS = Object.freeze({
  technology: '기술', operations: '운영', finance: '재무', integrated: '통합',
});

export const REPORT_ORDER = Object.freeze(['technology', 'operations', 'finance']);

export function canonicalizeReportModules(modules = []) {
  const selected = new Set(Array.isArray(modules) ? modules : []);
  return REPORT_ORDER.filter((module) => selected.has(module));
}

export function reportModulesFromQuery(reportType, searchParams) {
  return reportType === 'integrated'
    ? canonicalizeReportModules(searchParams.getAll('modules'))
    : canonicalizeReportModules([reportType]);
}

function compactKoreanUnit(value, unit, shortUnit, divisor) {
  if (!value) return null;
  if (value % divisor === 0) return `${value / divisor}${shortUnit}${unit}`;
  return `${value.toLocaleString('ko-KR')}${unit}`;
}

export function formatKrwAmount(value) {
  const numeric = Number(value ?? 0);
  const safeNumber = Number.isFinite(numeric) ? numeric : 0;
  if (!Number.isInteger(safeNumber)) {
    const formatted = safeNumber.toLocaleString('ko-KR', { maximumFractionDigits: 2 });
    return { raw: `${formatted} KRW`, readable: `${formatted}원` };
  }
  const integer = safeNumber;
  const sign = integer < 0 ? '-' : '';
  const absolute = Math.abs(integer);
  const raw = `${integer.toLocaleString('ko-KR')} KRW`;
  if (absolute === 0) return { raw, readable: '0원' };

  const billion = Math.floor(absolute / 100_000_000);
  const remainderAfterBillion = absolute % 100_000_000;
  const tenThousands = Math.floor(remainderAfterBillion / 10_000);
  const won = remainderAfterBillion % 10_000;
  const parts = [];
  if (billion) parts.push(`${billion.toLocaleString('ko-KR')}억`);
  if (tenThousands) {
    const compact = tenThousands < 1_000
      ? compactKoreanUnit(tenThousands, '만', '백', 100)
      : compactKoreanUnit(tenThousands, '만', '천', 1_000);
    parts.push(compact);
  }
  if (won) parts.push(`${won.toLocaleString('ko-KR')}원`);
  else parts[parts.length - 1] = `${parts.at(-1)} 원`;
  return { raw, readable: `${sign}${parts.join(' ')}` };
}

function compactTimestamp(value) {
  const match = String(value ?? '').match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})/);
  if (match) return `${match[1]}${match[2]}${match[3]}_${match[4]}${match[5]}`;
  const now = new Date();
  const part = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}${part(now.getMonth() + 1)}${part(now.getDate())}_${part(now.getHours())}${part(now.getMinutes())}`;
}

export function sanitizeReportFilenamePart(value, fallback = '프로젝트', maxLength = 72) {
  const normalized = String(value ?? fallback)
    .replace(/[<>:"/\\|?*]+/g, ' ')
    .trim()
    .replace(/\s+/g, '_')
    .replace(/[. ]+$/g, '');
  return (normalized || fallback).slice(0, maxLength);
}

export function launchReadinessReportTitle(projectName, reportType, completedAt) {
  const project = sanitizeReportFilenamePart(projectName);
  if (reportType === 'integrated') return `${project}_출시준비_통합보고서_${compactTimestamp(completedAt)}`;
  const label = REPORT_LABELS[reportType] ?? REPORT_LABELS.integrated;
  return `${project}_${label}_출시준비_보고서_${compactTimestamp(completedAt)}`;
}

export function printLaunchReadinessReport(projectName, reportType, completedAt) {
  const originalTitle = document.title;
  const restore = () => { document.title = originalTitle; };
  document.title = launchReadinessReportTitle(projectName, reportType, completedAt);
  window.addEventListener('afterprint', restore, { once: true });
  window.print();
}

export function formatReportDate(value) {
  if (!value) return '자료 없음';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return String(value);
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeStyle: 'short' }).format(parsed);
}
