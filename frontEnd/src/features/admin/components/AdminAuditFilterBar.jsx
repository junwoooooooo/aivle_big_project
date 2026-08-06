import { Select, TextInput } from '../../../shared/ui/index.js';
import { ADMIN_AUDIT_ACTIONS, getAuditActionLabel } from '../model/auditLabels.js';

export default function AdminAuditFilterBar({
  values,
  actor,
  requestId,
  onSearchChange,
  onCompositionStart,
  onCompositionEnd,
  onFilterChange,
}) {
  return (
    <div className="admin-toolbar admin-audit-filters" aria-label="감사 로그 필터">
      <TextInput
        label="관리자"
        value={actor}
        placeholder="Username, 이름 또는 ID"
        onChange={(event) => onSearchChange('actor', event.target.value)}
        onCompositionStart={() => onCompositionStart('actor')}
        onCompositionEnd={(event) => onCompositionEnd('actor', event.currentTarget.value)}
      />
      <Select label="작업" value={values.action} onChange={(event) => onFilterChange('action', event.target.value)}>
        <option value="">전체</option>
        {ADMIN_AUDIT_ACTIONS.map((action) => <option key={action} value={action}>{getAuditActionLabel(action)}</option>)}
      </Select>
      <Select label="결과" value={values.result} onChange={(event) => onFilterChange('result', event.target.value)}>
        <option value="">전체</option>
        <option value="SUCCESS">성공</option>
        <option value="FAILED">실패</option>
      </Select>
      <Select label="대상 유형" value={values.targetType} onChange={(event) => onFilterChange('targetType', event.target.value)}>
        <option value="">전체</option>
        <option value="USER">사용자</option>
        <option value="PROJECT">프로젝트</option>
        <option value="SERVICE_SETTING">서비스 설정</option>
        <option value="ADMIN_AUTH">관리자 인증</option>
        <option value="OTHER">기타</option>
      </Select>
      <TextInput
        label="Request ID"
        value={requestId}
        placeholder="정확한 Request ID"
        onChange={(event) => onSearchChange('requestId', event.target.value)}
        onCompositionStart={() => onCompositionStart('requestId')}
        onCompositionEnd={(event) => onCompositionEnd('requestId', event.currentTarget.value)}
      />
      <TextInput label="시작일" type="date" value={values.occurredFrom} onChange={(event) => onFilterChange('occurredFrom', event.target.value)} />
      <TextInput label="종료일" type="date" value={values.occurredTo} onChange={(event) => onFilterChange('occurredTo', event.target.value)} />
      <Select label="페이지 크기" value={String(values.size)} onChange={(event) => onFilterChange('size', event.target.value)}>
        <option value="20">20개</option>
        <option value="50">50개</option>
        <option value="100">100개</option>
      </Select>
      <Select label="정렬" value={values.sort} onChange={(event) => onFilterChange('sort', event.target.value)}>
        <option value="occurredAt,desc">최신순</option>
        <option value="occurredAt,asc">오래된순</option>
        <option value="actorUsername,asc">관리자 Username순</option>
        <option value="action,asc">작업명순</option>
        <option value="result,asc">결과순</option>
      </Select>
    </div>
  );
}
