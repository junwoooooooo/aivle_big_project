export const BUSINESS_PLAN_MAX_SIZE = 20 * 1024 * 1024;

export const BUSINESS_PLAN_ACCEPT = '.docx';

const DOCX_MIME =
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document';

export function formatFileSize(size) {
  if (size < 1024 * 1024) {
    return `${Math.ceil(size / 1024)}KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)}MB`;
}

export function validateBusinessPlanFile(file) {
  if (!file) return 'DOCX 파일을 선택해 주세요.';
  if (!file.name.toLowerCase().endsWith('.docx')) {
    return 'DOCX 파일만 선택할 수 있습니다.';
  }
  if (file.type && file.type !== DOCX_MIME) {
    return '파일 형식이 DOCX 확장자와 일치하지 않습니다.';
  }
  if (file.size > BUSINESS_PLAN_MAX_SIZE) {
    return '파일 크기는 최대 20MB까지 허용됩니다.';
  }
  if (file.size === 0) {
    return '비어 있는 파일은 업로드할 수 없습니다.';
  }
  return '';
}
