import { Link } from 'react-router-dom';

import AdminStatusBadge from './AdminStatusBadge.jsx';
import { getAuditActionLabel } from '../model/auditLabels.js';

function date(value) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value));
}

export default function AdminAuditTable({ events, location }) {
  const returnTo = `${location.pathname}${location.search}`;
  return (
    <div className="admin-table-scroll">
      <table className="admin-table admin-audit-table">
        <caption className="visually-hidden">관리자 감사 로그</caption>
        <thead>
          <tr>
            <th scope="col">시각</th>
            <th scope="col">수행 관리자</th>
            <th scope="col">작업</th>
            <th scope="col">대상</th>
            <th scope="col">결과</th>
            <th scope="col">사유</th>
            <th scope="col">Request ID</th>
            <th scope="col">상세</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => {
            const destination = `/admin/audit/${event.id}${location.search}`;
            const state = { backgroundLocation: location, returnTo };
            return (
              <tr key={event.id}>
                <td><time dateTime={event.occurredAt}>{date(event.occurredAt)}</time></td>
                <td>
                  {event.actor.displayName || event.actor.username || '알 수 없음'}
                  {event.actor.username && <small className="admin-table-secondary">@{event.actor.username}</small>}
                </td>
                <td>{getAuditActionLabel(event.action)}</td>
                <td>{event.target.label || `${event.target.type} ${event.target.id ? `#${event.target.id}` : ''}`}</td>
                <td><AdminStatusBadge value={event.result} /></td>
                <td>{event.reason || '—'}</td>
                <td><code className="admin-code-value">{event.requestId || '—'}</code></td>
                <td>
                  <Link className="admin-detail-link" to={destination} state={state} aria-label={`${getAuditActionLabel(event.action)} 감사 상세 보기`}>
                    상세
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
