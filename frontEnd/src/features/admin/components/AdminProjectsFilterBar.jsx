import { Select, TextInput } from '../../../shared/ui/index.js';

export default function AdminProjectsFilterBar({
  values, keyword, owner, onSearchChange, onCompositionStart, onCompositionEnd, onFilterChange,
}) {
  return (
    <div className="admin-toolbar admin-project-filters" aria-label="프로젝트 목록 필터">
      <TextInput label="프로젝트명" value={keyword} placeholder="프로젝트명 또는 설명"
        onChange={(event) => onSearchChange('keyword', event.target.value)}
        onCompositionStart={() => onCompositionStart('keyword')}
        onCompositionEnd={(event) => onCompositionEnd('keyword', event.currentTarget.value)} />
      <TextInput label="소유자" value={owner} placeholder="Username, 이름 또는 ID"
        onChange={(event) => onSearchChange('owner', event.target.value)}
        onCompositionStart={() => onCompositionStart('owner')}
        onCompositionEnd={(event) => onCompositionEnd('owner', event.currentTarget.value)} />
      <Select label="Status" value={values.status} onChange={(event) => onFilterChange('status', event.target.value)}>
        <option value="">전체</option>
        {['DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED'].map((status) => (
          <option key={status} value={status}>{status}</option>
        ))}
      </Select>
      <TextInput label="업종" value={values.industryCategory} placeholder="저장된 업종 값"
        onChange={(event) => onFilterChange('industryCategory', event.target.value)} />
      <TextInput label="생성 시작일" type="date" value={values.createdFrom}
        onChange={(event) => onFilterChange('createdFrom', event.target.value)} />
      <TextInput label="생성 종료일" type="date" value={values.createdTo}
        onChange={(event) => onFilterChange('createdTo', event.target.value)} />
      <Select label="페이지 크기" value={String(values.size)} onChange={(event) => onFilterChange('size', event.target.value)}>
        {[10, 20, 50, 100].map((size) => <option key={size} value={size}>{size}개</option>)}
      </Select>
      <Select label="정렬" value={values.sort} onChange={(event) => onFilterChange('sort', event.target.value)}>
        <option value="updatedAt,desc">최근 수정순</option>
        <option value="createdAt,desc">최근 생성순</option>
        <option value="createdAt,asc">생성일 오름차순</option>
        <option value="title,asc">프로젝트명 오름차순</option>
        <option value="status,asc">Status 오름차순</option>
      </Select>
    </div>
  );
}
