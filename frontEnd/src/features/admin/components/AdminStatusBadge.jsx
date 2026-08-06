const LABELS = {
  ACTIVE: '활성',
  LOCKED: '잠김',
  DISABLED: '비활성',
  USER: 'USER',
  ADMIN: 'ADMIN',
  SUCCESS: '성공',
  FAILED: '실패',
  QUEUED: '대기',
  READY: '실행 준비',
  RUNNING: '실행 중',
  SUCCEEDED: '성공',
  CANCELLED: '취소',
  TIMED_OUT: '시간 초과',
};

export default function AdminStatusBadge({ value }) {
  const normalized = String(value || 'UNKNOWN').toUpperCase();
  return (
    <span className={`admin-status admin-status--${normalized.toLowerCase()}`}>
      {LABELS[normalized] || normalized}
    </span>
  );
}
