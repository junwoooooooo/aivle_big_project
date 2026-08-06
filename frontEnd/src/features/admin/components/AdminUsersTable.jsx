import { Link } from 'react-router-dom';

import AdminStatusBadge from './AdminStatusBadge.jsx';

function date(value, withTime = false) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', withTime
    ? { dateStyle: 'medium', timeStyle: 'short' }
    : { dateStyle: 'medium' }).format(new Date(value));
}

export default function AdminUsersTable({ users, location }) {
  const returnTo = `${location.pathname}${location.search}`;
  return (
    <div className="admin-table-scroll">
      <table className="admin-table admin-users-table">
        <caption className="visually-hidden">관리자 사용자 목록</caption>
        <thead>
          <tr>
            <th scope="col">사용자</th>
            <th scope="col">Username</th>
            <th scope="col">Email</th>
            <th scope="col">Role</th>
            <th scope="col">상태</th>
            <th scope="col">프로젝트 수</th>
            <th scope="col">최근 로그인</th>
            <th scope="col">가입일</th>
            <th scope="col">작업</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => {
            const destination = `/admin/users/${user.id}${location.search}`;
            const routeState = { backgroundLocation: location, returnTo };
            return (
              <tr key={user.id}>
                <td>
                  <Link className="admin-user-link" to={destination} state={routeState}>
                    {user.displayName || user.username}
                  </Link>
                </td>
                <td>@{user.username}</td>
                <td>{user.email || '—'}</td>
                <td><AdminStatusBadge value={user.role} /></td>
                <td><AdminStatusBadge value={user.accountStatus} /></td>
                <td>{user.projectCount.toLocaleString()}</td>
                <td>{date(user.lastLoginAt, true)}</td>
                <td>{date(user.createdAt)}</td>
                <td>
                  <Link className="admin-detail-link" to={destination} state={routeState} aria-label={`${user.username} 상세 보기`}>
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
