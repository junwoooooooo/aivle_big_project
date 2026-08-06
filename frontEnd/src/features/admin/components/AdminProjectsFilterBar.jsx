import { Select, TextInput } from '../../../shared/ui/index.js';

const STAGES = [
  'DOCUMENT',
  'STRUCTURING',
  'LEGAL_REVIEW',
  'FEASIBILITY',
  'FINANCIAL',
  'PERSONA_CONFIGURATION',
  'PANEL_SURVEY',
  'PANEL_DISCUSSION',
  'REPORT',
  'MARKETING',
  'COMPLETED',
];

export default function AdminProjectsFilterBar({
  values,
  keyword,
  owner,
  onSearchChange,
  onCompositionStart,
  onCompositionEnd,
  onFilterChange,
}) {
  return (
    <div className="admin-toolbar admin-project-filters" aria-label="프로젝트 목록 필터">
      <TextInput
        label="프로젝트명"
        value={keyword}
        placeholder="프로젝트명 또는 설명"
        onChange={(event) => onSearchChange('keyword', event.target.value)}
        onCompositionStart={() => onCompositionStart('keyword')}
        onCompositionEnd={(event) => onCompositionEnd('keyword', event.currentTarget.value)}
      />
      <TextInput
        label="소유자"
        value={owner}
        placeholder="Username, 이름 또는 ID"
        onChange={(event) => onSearchChange('owner', event.target.value)}
        onCompositionStart={() => onCompositionStart('owner')}
        onCompositionEnd={(event) => onCompositionEnd('owner', event.currentTarget.value)}
      />
      <Select label="Area" value={values.area} onChange={(event) => onFilterChange('area', event.target.value)}>
        <option value="">전체</option>
        <option value="PLAN">PLAN</option>
        <option value="REVIEW">REVIEW</option>
        <option value="VALIDATE">VALIDATE</option>
        <option value="REPORT">REPORT</option>
      </Select>
      <Select label="Status" value={values.status} onChange={(event) => onFilterChange('status', event.target.value)}>
        <option value="">전체</option>
        <option value="DRAFT">DRAFT</option>
        <option value="ACTIVE">ACTIVE</option>
        <option value="PAUSED">PAUSED</option>
        <option value="COMPLETED">COMPLETED</option>
        <option value="ARCHIVED">ARCHIVED</option>
      </Select>
      <Select label="Stage" value={values.stage} onChange={(event) => onFilterChange('stage', event.target.value)}>
        <option value="">전체</option>
        {STAGES.map((stage) => <option key={stage} value={stage}>{stage}</option>)}
      </Select>
      <TextInput
        label="업종"
        value={values.industryCategory}
        placeholder="저장된 업종 값"
        onChange={(event) => onFilterChange('industryCategory', event.target.value)}
      />
      <TextInput label="생성 시작일" type="date" value={values.createdFrom} onChange={(event) => onFilterChange('createdFrom', event.target.value)} />
      <TextInput label="생성 종료일" type="date" value={values.createdTo} onChange={(event) => onFilterChange('createdTo', event.target.value)} />
      <Select label="페이지 크기" value={String(values.size)} onChange={(event) => onFilterChange('size', event.target.value)}>
        <option value="10">10개</option>
        <option value="20">20개</option>
        <option value="50">50개</option>
        <option value="100">100개</option>
      </Select>
      <Select label="정렬" value={values.sort} onChange={(event) => onFilterChange('sort', event.target.value)}>
        <option value="updatedAt,desc">최근 수정순</option>
        <option value="createdAt,desc">최근 생성순</option>
        <option value="createdAt,asc">생성일 오래된순</option>
        <option value="title,asc">프로젝트명 오름차순</option>
        <option value="status,asc">Status 오름차순</option>
        <option value="stage,asc">Stage 오름차순</option>
      </Select>
    </div>
  );
}
