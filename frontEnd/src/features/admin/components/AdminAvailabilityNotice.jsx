const REASON_MESSAGES = {
  AI_SERVER_NOT_CONNECTED: '현재 AI 서버 상태를 수집할 수 없습니다.',
  'CONFIGURED:AVAILABLE': '내부 AI 실행 연결이 설정되어 있습니다.',
  'NOT_CONFIGURED:UNAVAILABLE': '내부 AI 실행 Token 또는 주소가 설정되지 않았습니다.',
};

export default function AdminAvailabilityNotice({ title, availability }) {
  if (!availability) return null;
  if (availability.available) {
    return (
      <section className="admin-panel admin-availability">
        <h2>{title}</h2>
        <strong>AVAILABLE</strong>
        <p>{REASON_MESSAGES[availability.reason] || '내부 AI 실행 연결을 사용할 수 있습니다.'}</p>
      </section>
    );
  }
  return (
    <section className="admin-panel admin-availability" aria-labelledby="admin-availability-title">
      <h2 id="admin-availability-title">{title}</h2>
      <strong>NOT_CONFIGURED / UNAVAILABLE</strong>
      <p>{REASON_MESSAGES[availability.reason] || '현재 외부 서비스 상태를 확인할 수 없습니다.'}</p>
      <small>연동 전에는 작업 수치나 운영 작업을 표시하지 않습니다.</small>
    </section>
  );
}
