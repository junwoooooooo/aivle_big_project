const REPORT_LABELS = Object.freeze({
  technology: '기술',
  operations: '운영',
  finance: '재무',
  integrated: '통합',
});

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
