import { Link } from 'react-router-dom';

import { Button, Spinner } from './controls.jsx';
import './ui.css';

export function Card({ as: Element = 'section', className = '', children, ...props }) {
  return (
    <Element className={`ui-card ${className}`} {...props}>
      {children}
    </Element>
  );
}

export function Badge({ tone = 'neutral', children }) {
  return <span className={`ui-badge ui-badge--${tone}`}>{children}</span>;
}

const STATUS_VIEW = {
  QUEUED: ['대기 중', 'info'],
  RUNNING: ['분석 중', 'info'],
  SUCCEEDED: ['완료', 'success'],
  PARTIAL: ['일부 보완 필요', 'warning'],
  FAILED: ['분석 오류', 'danger'],
  CANCELED: ['취소됨', 'neutral'],
  PRESENT: ['확인됨', 'success'],
  MISSING: ['보완 필요', 'warning'],
  OPEN: ['보완 필요', 'warning'],
  FILLED: ['입력 완료', 'success'],
  WAIVED: ['이번 단계에서 제외', 'neutral'],
  NEEDS_INPUT: ['보완 필요', 'warning'],
  CONFIRMED: ['확정 완료', 'success'],
  NEEDS_REVIEW: ['추가 확인 필요', 'warning'],
  INVALID: ['확인 필요', 'danger'],
  UNKNOWN: ['확인 필요', 'neutral'],
  PASS: ['통과', 'success'],
  DRAFT: ['작성 중', 'neutral'],
  ACTIVE: ['진행 중', 'info'],
  PAUSED: ['일시 중지', 'warning'],
  COMPLETED: ['완료', 'success'],
  ARCHIVED: ['보관됨', 'neutral'],
};

export function StatusBadge({ status }) {
  const [label, tone] = STATUS_VIEW[status] ?? ['상태 확인 필요', 'neutral'];
  return <Badge tone={tone}>{label}</Badge>;
}

export function Alert({ title, children, tone = 'info', live = true }) {
  return (
    <div
      className={`ui-alert ui-alert--${tone}`}
      role={tone === 'danger' ? 'alert' : 'status'}
      aria-live={live ? 'polite' : undefined}
    >
      {title && <strong>{title}</strong>}
      <div>{children}</div>
    </div>
  );
}

export function Skeleton({ label = '콘텐츠를 불러오는 중' }) {
  return (
    <div className="ui-skeleton" role="status">
      <span className="visually-hidden">{label}</span>
      <span aria-hidden="true" />
      <span aria-hidden="true" />
      <span aria-hidden="true" />
    </div>
  );
}

export function Progress({ value, label = '진행률' }) {
  const normalized = Math.min(100, Math.max(0, value));
  return (
    <div className="ui-progress">
      <div className="ui-progress__label">
        <span>{label}</span>
        <span>{normalized}%</span>
      </div>
      <progress max="100" value={normalized}>
        {normalized}%
      </progress>
    </div>
  );
}

export function EmptyState({ title, description, action }) {
  return (
    <Card className="ui-state">
      <span className="ui-state__mark" aria-hidden="true">○</span>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </Card>
  );
}

export function ErrorState({
  title = '요청을 완료하지 못했습니다',
  description = '잠시 후 다시 시도해 주세요.',
  onRetry,
}) {
  return (
    <Card className="ui-state" role="alert">
      <span className="ui-state__mark ui-state__mark--error" aria-hidden="true">!</span>
      <h2>{title}</h2>
      <p>{description}</p>
      {onRetry && <Button onClick={onRetry}>다시 시도</Button>}
    </Card>
  );
}

export function LoadingState({ label = '화면을 준비하고 있습니다' }) {
  return (
    <div className="ui-state ui-state--plain" role="status">
      <Spinner label={label} />
      <p>{label}</p>
    </div>
  );
}

export function Breadcrumb({ items }) {
  return (
    <nav aria-label="현재 위치">
      <ol className="ui-breadcrumb">
        {items.map((item, index) => (
          <li key={`${item.label}-${index}`}>
            {item.to ? <Link to={item.to}>{item.label}</Link> : <span aria-current="page">{item.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function PageHeader({ eyebrow, title, description, actions, breadcrumbs }) {
  return (
    <div className="ui-page-header">
      {breadcrumbs && <Breadcrumb items={breadcrumbs} />}
      {eyebrow && <p className="ui-page-header__eyebrow">{eyebrow}</p>}
      <div className="ui-page-header__row">
        <div>
          <h1 tabIndex="-1">{title}</h1>
          {description && <p>{description}</p>}
        </div>
        {actions && <div className="ui-page-header__actions">{actions}</div>}
      </div>
    </div>
  );
}

export function Pagination({ page, totalPages, onPageChange }) {
  return (
    <nav className="ui-pagination" aria-label="페이지">
      <Button variant="outline" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
        이전
      </Button>
      <span aria-live="polite">{page} / {totalPages}</span>
      <Button variant="outline" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
        다음
      </Button>
    </nav>
  );
}

export function Accordion({ title, children }) {
  return (
    <details className="ui-accordion">
      <summary>{title}</summary>
      <div>{children}</div>
    </details>
  );
}
