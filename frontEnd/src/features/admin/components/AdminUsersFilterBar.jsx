import { Select, TextInput } from '../../../shared/ui/index.js';

export default function AdminUsersFilterBar({
  keyword,
  role,
  status,
  size,
  sort,
  onKeywordChange,
  onCompositionStart,
  onCompositionEnd,
  onFilterChange,
}) {
  return (
    <div className="admin-toolbar admin-users-filters" aria-label="사용자 목록 필터">
      <TextInput
        label="사용자 검색"
        value={keyword}
        placeholder="Username, 이메일, 이름"
        onChange={(event) => onKeywordChange(event.target.value)}
        onCompositionStart={onCompositionStart}
        onCompositionEnd={(event) => onCompositionEnd(event.currentTarget.value)}
      />
      <Select label="역할" value={role} onChange={(event) => onFilterChange('role', event.target.value)}>
        <option value="">전체</option>
        <option value="USER">USER</option>
        <option value="ADMIN">ADMIN</option>
      </Select>
      <Select label="계정 상태" value={status} onChange={(event) => onFilterChange('status', event.target.value)}>
        <option value="">전체</option>
        <option value="ACTIVE">활성</option>
        <option value="LOCKED">잠김</option>
        <option value="DISABLED">비활성</option>
      </Select>
      <Select label="페이지 크기" value={String(size)} onChange={(event) => onFilterChange('size', event.target.value)}>
        <option value="10">10명</option>
        <option value="20">20명</option>
        <option value="50">50명</option>
        <option value="100">100명</option>
      </Select>
      <Select label="정렬" value={sort} onChange={(event) => onFilterChange('sort', event.target.value)}>
        <option value="createdAt,desc">가입일 최신순</option>
        <option value="createdAt,asc">가입일 오래된순</option>
        <option value="lastLoginAt,desc">최근 로그인순</option>
        <option value="username,asc">Username 오름차순</option>
        <option value="displayName,asc">이름 오름차순</option>
      </Select>
    </div>
  );
}
