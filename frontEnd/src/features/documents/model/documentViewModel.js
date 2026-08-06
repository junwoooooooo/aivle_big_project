const JOB_VIEW = {
  QUEUED: ['분석 대기 중', '서버가 문서 처리를 준비하고 있습니다.'],
  RUNNING: ['문서 분석 중', '사업계획서의 구조와 근거를 분석하고 있습니다.'],
  SUCCEEDED: ['분석 완료', '구조화 결과가 준비되었습니다.'],
  PARTIAL: ['일부 항목 보완 필요', '분석은 완료되었으며 보완할 항목이 있습니다.'],
  FAILED: ['분석 실패', '문서를 처리하지 못했습니다. 새 버전으로 다시 시도해 주세요.'],
  CANCELED: ['분석 취소', '문서 처리가 취소되었습니다.'],
};

export const ACTIVE_JOB_STATUSES = new Set(['QUEUED', 'RUNNING']);
export const RESULT_JOB_STATUSES = new Set(['SUCCEEDED', 'PARTIAL']);

export function toJobViewModel(job) {
  if (!job) return null;
  const [label, description] = JOB_VIEW[job.status] ?? [
    '상태 확인 필요',
    '최신 작업 상태를 다시 확인해 주세요.',
  ];
  return {
    ...job,
    label,
    description,
    progress: Number.isFinite(job.progress) ? job.progress : 0,
  };
}

export function formatDocumentDate(value) {
  if (!value) return '시간 정보 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '시간 확인 필요';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
