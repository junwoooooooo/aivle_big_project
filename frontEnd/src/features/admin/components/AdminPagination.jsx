import { Button } from '../../../shared/ui/index.js';

function visiblePages(current, total) {
  const start = Math.max(0, Math.min(current - 2, total - 5));
  const end = Math.min(total, start + 5);
  return Array.from({ length: Math.max(0, end - start) }, (_, index) => start + index);
}

export default function AdminPagination({
  page,
  totalPages,
  totalElements,
  first,
  last,
  onChange,
  itemLabel = '사용자',
  unit = '명',
}) {
  const pages = visiblePages(page, totalPages);
  return (
    <nav className="admin-pagination" aria-label={`${itemLabel} 목록 페이지`}>
      <span className="admin-pagination__total">전체 {totalElements.toLocaleString()}{unit}</span>
      <Button size="small" variant="outline" disabled={first} onClick={() => onChange(page - 1)}>이전</Button>
      <div className="admin-pagination__pages">
        {pages.map((number) => (
          <Button
            key={number}
            size="small"
            variant={number === page ? 'primary' : 'ghost'}
            aria-current={number === page ? 'page' : undefined}
            onClick={() => onChange(number)}
          >
            {number + 1}
          </Button>
        ))}
      </div>
      <Button size="small" variant="outline" disabled={last} onClick={() => onChange(page + 1)}>다음</Button>
    </nav>
  );
}
